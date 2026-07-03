package org.alexmond.jhelm.kube.service.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePluralizerTest {

	@ParameterizedTest
	@ValueSource(strings = { "ClusterRole", "ClusterRoleBinding", "Namespace", "Node", "PersistentVolume",
			"StorageClass", "CustomResourceDefinition", "PriorityClass", "IngressClass", "APIService",
			"MutatingWebhookConfiguration", "ValidatingWebhookConfiguration", "CSIDriver", "VolumeAttachment" })
	void testClusterScopedKindsAreNotNamespaced(String kind) {
		// #650: cluster-scoped kinds must resolve as not-namespaced from static tables
		// (no cluster).
		assertFalse(new ResourcePluralizer(null).isNamespaced(kind), kind + " should be cluster-scoped");
	}

	@ParameterizedTest
	@ValueSource(strings = { "ConfigMap", "Secret", "Service", "ServiceAccount", "Deployment", "StatefulSet", "Pod",
			"Role", "RoleBinding", "Ingress", "Job", "CronJob", "PersistentVolumeClaim", "NetworkPolicy" })
	void testNamespacedKindsAreNamespaced(String kind) {
		assertTrue(new ResourcePluralizer(null).isNamespaced(kind), kind + " should be namespaced");
	}

	@Test
	void testUnknownKindDefaultsToNamespaced() {
		// An unknown CRD with no reachable cluster (null client) defaults to namespaced.
		assertTrue(new ResourcePluralizer(null).isNamespaced("WidgetThing"));
	}

	@ParameterizedTest
	@CsvSource({ "Pod,pods", "Service,services", "ConfigMap,configmaps", "Secret,secrets", "Namespace,namespaces",
			"Deployment,deployments", "StatefulSet,statefulsets", "DaemonSet,daemonsets", "ReplicaSet,replicasets",
			"Job,jobs", "CronJob,cronjobs", "Ingress,ingresses", "IngressClass,ingressclasses",
			"NetworkPolicy,networkpolicies", "PodDisruptionBudget,poddisruptionbudgets", "ClusterRole,clusterroles",
			"ClusterRoleBinding,clusterrolebindings", "Role,roles", "RoleBinding,rolebindings",
			"HorizontalPodAutoscaler,horizontalpodautoscalers", "StorageClass,storageclasses",
			"PersistentVolume,persistentvolumes", "PersistentVolumeClaim,persistentvolumeclaims",
			"ServiceAccount,serviceaccounts", "Endpoints,endpoints", "Node,nodes", "LimitRange,limitranges",
			"ResourceQuota,resourcequotas", "MutatingWebhookConfiguration,mutatingwebhookconfigurations",
			"ValidatingWebhookConfiguration,validatingwebhookconfigurations",
			"CustomResourceDefinition,customresourcedefinitions", "PriorityClass,priorityclasses",
			"RuntimeClass,runtimeclasses", "EndpointSlice,endpointslices", "Lease,leases", "CSIDriver,csidrivers",
			"APIService,apiservices" })
	void testWellKnownPlurals(String kind, String expectedPlural) {
		// Construct with null apiClient — should still resolve from static map
		ResourcePluralizer pluralizer = new ResourcePluralizer(null);
		assertEquals(expectedPlural, pluralizer.toPlural(kind));
	}

	@ParameterizedTest
	@CsvSource({ "Ingress,ingresses", "NetworkPolicy,networkpolicies", "Endpoints,endpoints",
			"ComponentStatus,componentstatuses" })
	void testIrregularPluralsResolved(String kind, String expectedPlural) {
		ResourcePluralizer pluralizer = new ResourcePluralizer(null);
		assertEquals(expectedPlural, pluralizer.toPlural(kind));
	}

	@ParameterizedTest
	@CsvSource({ "MyCustomResource,mycustomresources", "FooBar,foobars", "MyPolicy,mypolicies",
			"SomeClass,someclasses" })
	void testHeuristicFallbackForUnknownKinds(String kind, String expectedPlural) {
		assertEquals(expectedPlural, ResourcePluralizer.heuristicPlural(kind));
	}

	@Test
	void testHeuristicHandlesEndingInSS() {
		assertEquals("ingresses", ResourcePluralizer.heuristicPlural("Ingress"));
	}

	@Test
	void testHeuristicHandlesEndingInCH() {
		assertEquals("batches", ResourcePluralizer.heuristicPlural("Batch"));
	}

	@Test
	void testHeuristicHandlesEndingInSH() {
		assertEquals("bushes", ResourcePluralizer.heuristicPlural("Bush"));
	}

	@Test
	void testHeuristicHandlesEndingInX() {
		assertEquals("boxes", ResourcePluralizer.heuristicPlural("Box"));
	}

	@Test
	void testHeuristicHandlesVowelPlusY() {
		assertEquals("keys", ResourcePluralizer.heuristicPlural("Key"));
	}

	@Test
	void testHeuristicPreservesAlreadyPluralEndpoints() {
		assertEquals("endpoints", ResourcePluralizer.heuristicPlural("Endpoints"));
	}

	@Test
	void testDiscoveryFailsGracefullyWithNullApiClient() {
		// When apiClient is null, discovery will fail but toPlural should still work
		// via static map and heuristics
		ResourcePluralizer pluralizer = new ResourcePluralizer(null);
		assertEquals("pods", pluralizer.toPlural("Pod"));
		// Unknown kind uses heuristics
		assertEquals("mywidgets", pluralizer.toPlural("MyWidget"));
	}

}
