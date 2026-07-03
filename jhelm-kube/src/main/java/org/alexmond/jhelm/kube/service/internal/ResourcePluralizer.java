package org.alexmond.jhelm.kube.service.internal;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.AutoscalingV2Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.apis.PolicyV1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.apis.StorageV1Api;
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.openapi.models.V1APIResourceList;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves Kubernetes resource kind names to their plural forms. Uses a combination of
 * well-known static mappings, API server discovery, and heuristic fallback.
 */
@Slf4j
class ResourcePluralizer {

	// @formatter:off
	private static final Map<String, String> WELL_KNOWN_PLURALS = Map.ofEntries(
		// core/v1
		Map.entry("Binding", "bindings"),
		Map.entry("ComponentStatus", "componentstatuses"),
		Map.entry("ConfigMap", "configmaps"),
		Map.entry("Endpoints", "endpoints"),
		Map.entry("Event", "events"),
		Map.entry("LimitRange", "limitranges"),
		Map.entry("Namespace", "namespaces"),
		Map.entry("Node", "nodes"),
		Map.entry("PersistentVolume", "persistentvolumes"),
		Map.entry("PersistentVolumeClaim", "persistentvolumeclaims"),
		Map.entry("Pod", "pods"),
		Map.entry("PodTemplate", "podtemplates"),
		Map.entry("ReplicationController", "replicationcontrollers"),
		Map.entry("ResourceQuota", "resourcequotas"),
		Map.entry("Secret", "secrets"),
		Map.entry("Service", "services"),
		Map.entry("ServiceAccount", "serviceaccounts"),
		// apps/v1
		Map.entry("ControllerRevision", "controllerrevisions"),
		Map.entry("DaemonSet", "daemonsets"),
		Map.entry("Deployment", "deployments"),
		Map.entry("ReplicaSet", "replicasets"),
		Map.entry("StatefulSet", "statefulsets"),
		// batch/v1
		Map.entry("CronJob", "cronjobs"),
		Map.entry("Job", "jobs"),
		// networking.k8s.io/v1
		Map.entry("Ingress", "ingresses"),
		Map.entry("IngressClass", "ingressclasses"),
		Map.entry("NetworkPolicy", "networkpolicies"),
		// policy/v1
		Map.entry("PodDisruptionBudget", "poddisruptionbudgets"),
		// rbac.authorization.k8s.io/v1
		Map.entry("ClusterRole", "clusterroles"),
		Map.entry("ClusterRoleBinding", "clusterrolebindings"),
		Map.entry("Role", "roles"),
		Map.entry("RoleBinding", "rolebindings"),
		// autoscaling/v2
		Map.entry("HorizontalPodAutoscaler", "horizontalpodautoscalers"),
		// storage.k8s.io/v1
		Map.entry("CSIDriver", "csidrivers"),
		Map.entry("CSINode", "csinodes"),
		Map.entry("CSIStorageCapacity", "csistoragecapacities"),
		Map.entry("StorageClass", "storageclasses"),
		Map.entry("VolumeAttachment", "volumeattachments"),
		// admissionregistration.k8s.io/v1
		Map.entry("MutatingWebhookConfiguration", "mutatingwebhookconfigurations"),
		Map.entry("ValidatingWebhookConfiguration", "validatingwebhookconfigurations"),
		Map.entry("ValidatingAdmissionPolicy", "validatingadmissionpolicies"),
		Map.entry("ValidatingAdmissionPolicyBinding", "validatingadmissionpolicybindings"),
		// apiextensions.k8s.io/v1
		Map.entry("CustomResourceDefinition", "customresourcedefinitions"),
		// certificates.k8s.io/v1
		Map.entry("CertificateSigningRequest", "certificatesigningrequests"),
		// coordination.k8s.io/v1
		Map.entry("Lease", "leases"),
		// discovery.k8s.io/v1
		Map.entry("EndpointSlice", "endpointslices"),
		// scheduling.k8s.io/v1
		Map.entry("PriorityClass", "priorityclasses"),
		// node.k8s.io/v1
		Map.entry("RuntimeClass", "runtimeclasses"),
		// flowcontrol.apiserver.k8s.io/v1
		Map.entry("FlowSchema", "flowschemas"),
		Map.entry("PriorityLevelConfiguration", "prioritylevelconfigurations"),
		// apiregistration.k8s.io/v1
		Map.entry("APIService", "apiservices")
	);

	/**
	 * Built-in Kubernetes kinds that live at the cluster scope (no namespace). Charts commonly
	 * ship the RBAC and CRD subset of these; applying any of them through the namespaced API
	 * path is rejected by the API server. Kinds not listed here (and not otherwise discovered)
	 * are assumed namespaced — the safe default, and correct for the vast majority of CRDs.
	 */
	private static final Set<String> CLUSTER_SCOPED_KINDS = Set.of(
		// core/v1
		"ComponentStatus", "Namespace", "Node", "PersistentVolume",
		// rbac.authorization.k8s.io/v1
		"ClusterRole", "ClusterRoleBinding",
		// storage.k8s.io/v1
		"CSIDriver", "CSINode", "StorageClass", "VolumeAttachment",
		// admissionregistration.k8s.io/v1
		"MutatingWebhookConfiguration", "ValidatingWebhookConfiguration",
		"ValidatingAdmissionPolicy", "ValidatingAdmissionPolicyBinding",
		// apiextensions.k8s.io/v1
		"CustomResourceDefinition",
		// certificates.k8s.io/v1
		"CertificateSigningRequest",
		// scheduling.k8s.io/v1
		"PriorityClass",
		// node.k8s.io/v1
		"RuntimeClass",
		// networking.k8s.io/v1
		"IngressClass",
		// flowcontrol.apiserver.k8s.io/v1
		"FlowSchema", "PriorityLevelConfiguration",
		// apiregistration.k8s.io/v1
		"APIService"
	);
	// @formatter:on

	private final ApiClient apiClient;

	private Map<String, String> discoveredPlurals;

	private Map<String, Boolean> discoveredNamespaced;

	ResourcePluralizer(ApiClient apiClient) {
		this.apiClient = apiClient;
	}

	/**
	 * Returns the plural form for a Kubernetes resource kind.
	 * @param kind the resource kind (e.g., "Ingress", "NetworkPolicy")
	 * @return the plural form (e.g., "ingresses", "networkpolicies")
	 */
	String toPlural(String kind) {
		// 1. Check well-known static mapping
		String known = WELL_KNOWN_PLURALS.get(kind);
		if (known != null) {
			return known;
		}

		// 2. Check discovery cache (lazy-loaded)
		if (discoveredPlurals == null) {
			discoverResources();
		}
		String discovered = discoveredPlurals.get(kind);
		if (discovered != null) {
			return discovered;
		}

		// 3. Heuristic fallback
		return heuristicPlural(kind);
	}

	/**
	 * Reports whether a Kubernetes resource kind is namespaced (versus cluster-scoped).
	 * Well-known cluster-scoped and namespaced built-ins are answered from static tables;
	 * anything else is resolved from API-server discovery, defaulting to namespaced when
	 * discovery is unavailable or the kind is unknown.
	 * @param kind the resource kind (e.g., "ClusterRole", "ConfigMap")
	 * @return {@code true} if the kind is namespaced, {@code false} if cluster-scoped
	 */
	boolean isNamespaced(String kind) {
		// 1. Known cluster-scoped built-in
		if (CLUSTER_SCOPED_KINDS.contains(kind)) {
			return false;
		}
		// 2. Any other well-known built-in is namespaced (cluster-scoped ones are caught
		// above)
		if (WELL_KNOWN_PLURALS.containsKey(kind)) {
			return true;
		}
		// 3. Discovery cache (lazy-loaded) — authoritative for CRDs
		if (discoveredNamespaced == null) {
			discoverResources();
		}
		Boolean namespaced = discoveredNamespaced.get(kind);
		// 4. Default to namespaced for unknown kinds (the common case for CRDs)
		return namespaced == null || namespaced;
	}

	private void discoverResources() {
		discoveredPlurals = new HashMap<>();
		discoveredNamespaced = new HashMap<>();
		try {
			addResourcesFrom(new CoreV1Api(apiClient).getAPIResources().execute());
			addResourcesFrom(new AppsV1Api(apiClient).getAPIResources().execute());
			addResourcesFrom(new BatchV1Api(apiClient).getAPIResources().execute());
			addResourcesFrom(new NetworkingV1Api(apiClient).getAPIResources().execute());
			addResourcesFrom(new PolicyV1Api(apiClient).getAPIResources().execute());
			addResourcesFrom(new RbacAuthorizationV1Api(apiClient).getAPIResources().execute());
			addResourcesFrom(new StorageV1Api(apiClient).getAPIResources().execute());
			addResourcesFrom(new AutoscalingV2Api(apiClient).getAPIResources().execute());
			if (log.isDebugEnabled()) {
				log.debug("API discovery loaded {} resource kind-plural mappings", discoveredPlurals.size());
			}
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("API discovery failed, relying on static mappings: {}", ex.getMessage());
			}
		}
	}

	private void addResourcesFrom(V1APIResourceList resourceList) {
		if (resourceList == null || resourceList.getResources() == null) {
			return;
		}
		for (V1APIResource resource : resourceList.getResources()) {
			// Skip subresources (e.g., "pods/status", "deployments/scale")
			if (resource.getName() != null && !resource.getName().contains("/") && resource.getKind() != null) {
				discoveredPlurals.putIfAbsent(resource.getKind(), resource.getName());
				if (resource.getNamespaced() != null) {
					discoveredNamespaced.putIfAbsent(resource.getKind(), resource.getNamespaced());
				}
			}
		}
	}

	static String heuristicPlural(String kind) {
		String lower = kind.toLowerCase(Locale.ROOT);
		// Already plural (e.g., "Endpoints")
		if (lower.endsWith("endpoints") || lower.endsWith("status")) {
			return lower;
		}
		// Words ending in "s", "x", "z", "ch", "sh" → add "es"
		if (lower.endsWith("ss") || lower.endsWith("x") || lower.endsWith("z") || lower.endsWith("ch")
				|| lower.endsWith("sh")) {
			return lower + "es";
		}
		// Words ending in consonant + "y" → replace "y" with "ies"
		if (lower.endsWith("y") && lower.length() > 1) {
			char beforeY = lower.charAt(lower.length() - 2);
			if (beforeY != 'a' && beforeY != 'e' && beforeY != 'i' && beforeY != 'o' && beforeY != 'u') {
				return lower.substring(0, lower.length() - 1) + "ies";
			}
		}
		// Default: add "s"
		return lower + "s";
	}

}
