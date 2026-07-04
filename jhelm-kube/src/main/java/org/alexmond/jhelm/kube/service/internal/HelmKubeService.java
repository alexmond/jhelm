package org.alexmond.jhelm.kube.service.internal;

import java.io.IOException;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.VersionInfo;
import io.kubernetes.client.util.Yaml;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.PatchOptions;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.alexmond.jhelm.core.exception.KubernetesOperationException;
import org.alexmond.jhelm.core.exception.ReleaseStorageException;
import org.alexmond.jhelm.core.exception.WaitTimeoutException;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.HelmReleaseCodec;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

/**
 * Default {@link KubeService} implementation backed by the official Kubernetes Java
 * client. It stores and reads Helm releases as gzip-compressed, base64-encoded Secrets
 * (matching Helm's {@code sh.helm.release.v1.*} storage format), applies rendered
 * manifests, and waits on workload readiness.
 *
 * <p>
 * Instances are created with a {@link KubeClient}. {@link KubeService} operations
 * translate the client's checked {@link ApiException} into the unchecked
 * {@link KubernetesOperationException} (preserving the HTTP status code), so the public
 * surface never leaks the kubernetes-client exception type.
 */
@Slf4j
public class HelmKubeService implements KubeService {

	/** Configured Kubernetes API client used for all cluster operations. */
	private final ApiClient apiClient;

	private final HelmReleaseCodec releaseCodec = new HelmReleaseCodec();

	private ResourcePluralizer pluralizer;

	/**
	 * Creates a service backed by the given {@link KubeClient}.
	 * @param kubeClient the jhelm Kubernetes client wrapper holding the configured API
	 * client
	 */
	public HelmKubeService(KubeClient kubeClient) {
		this.apiClient = kubeClient.apiClient();
	}

	/**
	 * Reads the live server version so {@code .Capabilities.KubeVersion} reflects the
	 * real target cluster during install/upgrade. Falls back to
	 * {@link Capabilities#DEFAULT} (engine default version) when the server cannot be
	 * reached, so a dry-run/offline path still renders. API versions are left at the
	 * engine default set.
	 */
	@Override
	public Capabilities getCapabilities() {
		try {
			VersionInfo info = new VersionApi(apiClient).getCode().execute();
			String gitVersion = (info != null) ? info.getGitVersion() : null;
			if (gitVersion != null && !gitVersion.isBlank()) {
				return new Capabilities(gitVersion, List.of());
			}
		}
		catch (ApiException | RuntimeException ex) {
			log.debug("Could not read server version for .Capabilities; using engine default", ex);
		}
		return Capabilities.DEFAULT;
	}

	/**
	 * Stores a release as a Secret in Kubernetes, matching Helm's storage format.
	 */
	@Override
	public void storeRelease(Release release) {
		CoreV1Api api = new CoreV1Api(apiClient);
		String name = "sh.helm.release.v1." + release.getName() + ".v" + release.getVersion();

		try {
			byte[] encoded = encodeRelease(release);

			V1Secret secret = new V1Secret()
				.metadata(new V1ObjectMeta().name(name)
					.namespace(release.getNamespace())
					.putLabelsItem("owner", "helm")
					.putLabelsItem("name", release.getName())
					.putLabelsItem("status",
							(release.getInfo().getStatus() != null) ? release.getInfo().getStatus().getValue() : null)
					.putLabelsItem("version", String.valueOf(release.getVersion())))
				.type("helm.sh/release.v1")
				.putDataItem("release", encoded);

			try {
				api.createNamespacedSecret(release.getNamespace(), secret).execute();
			}
			catch (Exception ex) {
				if (ex instanceof ApiException ae && ae.getCode() == 409) {
					api.replaceNamespacedSecret(name, release.getNamespace(), secret).execute();
				}
				else {
					throw ex;
				}
			}
		}
		catch (Exception ex) {
			throw new ReleaseStorageException("Failed to store release: " + release.getName(), ex);
		}
	}

	/**
	 * Retrieves the latest version of a release from Kubernetes.
	 */
	@Override
	public Optional<Release> getRelease(String name, String namespace) {
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm,name=" + name;

		V1SecretList list = listSecrets(api, namespace, labelSelector, "read release " + name);

		Optional<V1Secret> latest = list.getItems().stream().sorted((s1, s2) -> {
			int v1 = Integer.parseInt(s1.getMetadata().getLabels().get("version"));
			int v2 = Integer.parseInt(s2.getMetadata().getLabels().get("version"));
			return Integer.compare(v2, v1); // Descending
		}).findFirst();

		if (latest.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(decodeRelease(latest.get()));
	}

	/**
	 * Retrieves all releases in a namespace.
	 */
	@Override
	public List<Release> listReleases(String namespace) {
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm";

		V1SecretList list = listSecrets(api, namespace, labelSelector, "list releases in " + namespace);

		// Group by name and pick the highest version for each
		Map<String, List<V1Secret>> grouped = list.getItems()
			.stream()
			.collect(Collectors.groupingBy((s) -> s.getMetadata().getLabels().get("name")));

		List<V1Secret> latestPerName = grouped.values()
			.stream()
			.map((secrets) -> secrets.stream()
				.max(Comparator.comparingInt((s) -> Integer.parseInt(s.getMetadata().getLabels().get("version"))))
				.orElse(null))
			.filter(Objects::nonNull)
			.toList();

		List<Release> releases = new ArrayList<>();
		for (V1Secret secret : latestPerName) {
			try {
				releases.add(decodeRelease(secret));
			}
			catch (ReleaseStorageException ex) {
				if (log.isWarnEnabled()) {
					log.warn("Failed to decode release from Secret {}: {}", secret.getMetadata().getName(),
							ex.getMessage());
				}
			}
		}
		return releases;
	}

	/**
	 * Retrieves all versions of a specific release.
	 */
	@Override
	public List<Release> getReleaseHistory(String name, String namespace) {
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm,name=" + name;

		V1SecretList list = listSecrets(api, namespace, labelSelector, "read history for " + name);

		List<V1Secret> sorted = list.getItems()
			.stream()
			.sorted(Comparator
				.comparingInt((V1Secret s) -> Integer.parseInt(s.getMetadata().getLabels().get("version")))
				.reversed())
			.toList();

		List<Release> history = new ArrayList<>();
		for (V1Secret secret : sorted) {
			history.add(decodeRelease(secret));
		}
		return history;
	}

	/**
	 * Deletes all versions of a release from Kubernetes.
	 */
	@Override
	public void deleteReleaseHistory(String name, String namespace) {
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm,name=" + name;
		V1SecretList list = listSecrets(api, namespace, labelSelector, "delete history for " + name);
		try {
			for (V1Secret secret : list.getItems()) {
				api.deleteNamespacedSecret(secret.getMetadata().getName(), namespace).execute();
			}
		}
		catch (ApiException ex) {
			throw new KubernetesOperationException("Failed to delete release history for " + name, ex, ex.getCode());
		}
	}

	/**
	 * Prunes a release's revision history to the newest {@code maxHistory} revisions,
	 * deleting the oldest Secrets. The highest-version (current) revision is never
	 * deleted.
	 */
	@Override
	public void pruneReleaseHistory(String name, String namespace, int maxHistory) {
		if (maxHistory <= 0) {
			return;
		}
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm,name=" + name;
		V1SecretList list = listSecrets(api, namespace, labelSelector, "prune history for " + name);

		List<V1Secret> sorted = list.getItems()
			.stream()
			.sorted(Comparator
				.comparingInt((V1Secret s) -> Integer.parseInt(s.getMetadata().getLabels().get("version"))))
			.toList();

		int toDelete = sorted.size() - maxHistory;
		if (toDelete <= 0) {
			return;
		}

		try {
			for (V1Secret secret : sorted.subList(0, toDelete)) {
				api.deleteNamespacedSecret(secret.getMetadata().getName(), namespace).execute();
			}
		}
		catch (ApiException ex) {
			throw new KubernetesOperationException("Failed to prune release history for " + name, ex, ex.getCode());
		}
	}

	/**
	 * Lists release Secrets for a label selector, translating the kubernetes-client
	 * {@link ApiException} into a {@link KubernetesOperationException} so callers never
	 * see the client's checked exception.
	 */
	private V1SecretList listSecrets(CoreV1Api api, String namespace, String labelSelector, String operation) {
		try {
			return api.listNamespacedSecret(namespace).labelSelector(labelSelector).execute();
		}
		catch (ApiException ex) {
			throw new KubernetesOperationException("Failed to " + operation, ex, ex.getCode());
		}
	}

	/**
	 * Lists the names of all pods in the given namespace, mimicking basic
	 * {@code helm status} pod listing.
	 * @param namespace the Kubernetes namespace to list pods from
	 * @return the names of the pods found in the namespace, in API-returned order
	 * @throws KubernetesOperationException if the Kubernetes API call fails
	 */
	public List<String> listPods(String namespace) {
		CoreV1Api api = new CoreV1Api(apiClient);
		try {
			V1PodList list = api.listNamespacedPod(namespace).execute();
			return list.getItems().stream().map((pod) -> pod.getMetadata().getName()).collect(Collectors.toList());
		}
		catch (ApiException ex) {
			throw new KubernetesOperationException("Failed to list pods in " + namespace, ex, ex.getCode());
		}
	}

	/**
	 * Creates the namespace if it does not already exist. An HTTP 409 conflict (the
	 * namespace already exists) is treated as success.
	 */
	@Override
	public void ensureNamespace(String namespace) {
		CoreV1Api api = new CoreV1Api(apiClient);
		V1Namespace ns = new V1Namespace().metadata(new V1ObjectMeta().name(namespace));
		try {
			if (log.isInfoEnabled()) {
				log.info("Creating namespace {}", namespace);
			}
			api.createNamespace(ns).execute();
		}
		catch (ApiException ex) {
			if (ex.getCode() == 409) { // Conflict / already exists -> no-op
				return;
			}
			throw new KubernetesOperationException("Failed to create namespace " + namespace, ex, ex.getCode());
		}
	}

	/**
	 * Applies a YAML manifest which can contain multiple resources.
	 */
	@Override
	public void apply(String namespace, String yamlContent) {
		applyManifest(namespace, yamlContent, false);
	}

	@Override
	public void applyDryRun(String namespace, String yamlContent) {
		applyManifest(namespace, yamlContent, true);
	}

	private void applyManifest(String namespace, String yamlContent, boolean serverDryRun) {
		try {
			for (Object doc : loadUnstructured(yamlContent)) {
				if (doc instanceof Map<?, ?> map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> resource = (Map<String, Object>) map;
					applyResource(namespace, resource, serverDryRun);
				}
			}
		}
		catch (ApiException ex) {
			throw new KubernetesOperationException("Failed to apply manifest", ex, ex.getCode());
		}
		catch (Exception ex) {
			throw new KubernetesOperationException("Failed to apply manifest", ex);
		}
	}

	private void applyResource(String namespace, Map<String, Object> resource, boolean serverDryRun)
			throws ApiException {
		String[] id = identify(resource);
		String apiVersion = id[0];
		String kind = id[1];
		String name = id[2];
		String group = apiVersion.contains("/") ? apiVersion.split("/")[0] : "";
		String version = apiVersion.contains("/") ? apiVersion.split("/")[1] : apiVersion;
		String plural = inferPlural(kind);
		boolean namespaced = inferNamespaced(kind);

		if (log.isInfoEnabled()) {
			log.info("{} {} ({}/{}) {}{}", serverDryRun ? "Validating (server dry-run)" : "Applying", kind, group,
					version, name, namespaced ? " in namespace " + namespace : " (cluster-scoped)");
		}

		V1Patch patch = new V1Patch(dumpUnstructured(resource));
		PatchOptions options = new PatchOptions();
		options.setFieldManager("helm");
		options.setForce(true);
		if (serverDryRun) {
			options.setDryRun("All");
		}
		// DynamicKubernetesApi routes the core group (empty group) to /api/<version> and
		// named groups to /apis/<group>/<version> (CustomObjectsApi would 404 the core
		// group);
		// cluster-scoped kinds skip the namespace segment based on the kind, not the
		// release ns.
		DynamicKubernetesApi api = new DynamicKubernetesApi(group, version, plural, apiClient);
		KubernetesApiResponse<DynamicKubernetesObject> response = namespaced
				? api.patch(namespace, name, V1Patch.PATCH_FORMAT_APPLY_YAML, patch, options)
				: api.patch(name, V1Patch.PATCH_FORMAT_APPLY_YAML, patch, options);
		response.throwsApiException();
	}

	// Parse manifests as UNSTRUCTURED maps, not typed io.kubernetes.client models: the
	// typed
	// round-trip materializes optional fields the manifest omits (e.g. V1PodSpec.overhead
	// ->
	// `overhead: {}`, which PodOverhead admission rejects, #666) and drops CRDs/unknown
	// kinds
	// (they deserialize as Map). SafeConstructor blocks arbitrary type instantiation via
	// tags.
	static Iterable<Object> loadUnstructured(String yamlContent) {
		return new org.yaml.snakeyaml.Yaml(new SafeConstructor(new LoaderOptions())).loadAll(yamlContent);
	}

	static String dumpUnstructured(Map<String, Object> resource) {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		return new org.yaml.snakeyaml.Yaml(options).dump(resource);
	}

	// [apiVersion, kind, name] from an unstructured doc; shared by apply/delete/status so
	// all
	// three handle the same kinds (incl. CRDs). Throws if any field is absent.
	@SuppressWarnings("unchecked")
	private static String[] identify(Map<String, Object> resource) {
		String apiVersion = (String) resource.get("apiVersion");
		String kind = (String) resource.get("kind");
		Object metadata = resource.get("metadata");
		String name = (metadata instanceof Map<?, ?>) ? (String) ((Map<String, Object>) metadata).get("name") : null;
		if (apiVersion == null || kind == null || name == null) {
			throw new KubernetesOperationException("Manifest document missing apiVersion/kind/metadata.name");
		}
		return new String[] { apiVersion, kind, name };
	}

	/**
	 * Deletes resources in a YAML manifest.
	 */
	@Override
	public void delete(String namespace, String yamlContent) {
		try {
			for (Object doc : loadUnstructured(yamlContent)) {
				if (doc instanceof Map<?, ?> map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> resource = (Map<String, Object>) map;
					deleteResource(namespace, resource);
				}
			}
		}
		catch (ApiException ex) {
			throw new KubernetesOperationException("Failed to delete manifest", ex, ex.getCode());
		}
		catch (Exception ex) {
			throw new KubernetesOperationException("Failed to delete manifest", ex);
		}
	}

	private void deleteResource(String namespace, Map<String, Object> resource) throws ApiException {
		String[] id = identify(resource);
		String apiVersion = id[0];
		String kind = id[1];
		String name = id[2];
		String group = apiVersion.contains("/") ? apiVersion.split("/")[0] : "";
		String version = apiVersion.contains("/") ? apiVersion.split("/")[1] : apiVersion;
		String plural = inferPlural(kind);
		boolean namespaced = inferNamespaced(kind);

		if (log.isInfoEnabled()) {
			log.info("Deleting {} {}{}", kind, name, namespaced ? " in namespace " + namespace : " (cluster-scoped)");
		}

		// Same core-group vs named-group routing and kind-based scoping as applyResource.
		DynamicKubernetesApi api = new DynamicKubernetesApi(group, version, plural, apiClient);
		KubernetesApiResponse<DynamicKubernetesObject> response = namespaced ? api.delete(namespace, name)
				: api.delete(name);
		if (!response.isSuccess() && response.getHttpStatusCode() != 404) {
			response.throwsApiException();
		}
	}

	/**
	 * Returns the readiness status for each resource in the rendered manifest.
	 */
	@Override
	public List<ResourceStatus> getResourceStatuses(String namespace, String manifest) {
		List<ResourceStatus> statuses = new ArrayList<>();
		Iterable<Object> objects;
		try {
			objects = loadUnstructured(manifest);
		}
		catch (Exception ex) {
			throw new KubernetesOperationException("Failed to parse manifest", ex);
		}
		for (Object obj : objects) {
			if (obj instanceof Map<?, ?> map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> resource = (Map<String, Object>) map;
				statuses.add(checkResourceStatus(namespace, resource));
			}
		}
		return statuses;
	}

	private ResourceStatus checkResourceStatus(String namespace, Map<String, Object> resource) {
		String[] id = identify(resource);
		String kind = id[1];
		String name = id[2];
		try {
			return switch (kind) {
				case "Deployment" -> checkDeployment(namespace, name);
				case "ReplicaSet" -> checkReplicaSet(namespace, name);
				case "DaemonSet" -> checkDaemonSet(namespace, name);
				case "StatefulSet" -> checkStatefulSet(namespace, name);
				case "Job" -> checkJob(namespace, name);
				case "Pod" -> checkPod(namespace, name);
				default -> ResourceStatus.builder()
					.kind(kind)
					.name(name)
					.namespace(namespace)
					.ready(true)
					.message("ready")
					.build();
			};
		}
		catch (Exception ex) {
			return ResourceStatus.builder()
				.kind(kind)
				.name(name)
				.namespace(namespace)
				.ready(false)
				.message(ex.getMessage())
				.build();
		}
	}

	private ResourceStatus checkDeployment(String namespace, String name) throws ApiException {
		AppsV1Api api = new AppsV1Api(apiClient);
		V1Deployment dep = api.readNamespacedDeployment(name, namespace).execute();
		int desired = (dep.getSpec().getReplicas() != null) ? dep.getSpec().getReplicas() : 1;
		int ready = nz(dep.getStatus().getReadyReplicas());
		int updated = nz(dep.getStatus().getUpdatedReplicas());
		int available = nz(dep.getStatus().getAvailableReplicas());
		boolean observed = observedCurrent(generationOf(dep.getMetadata()), dep.getStatus().getObservedGeneration());
		boolean isReady = observed && (updated >= desired) && (ready >= desired) && (available >= desired);
		String message = isReady ? "ready"
				: !observed ? "waiting for rollout" : String.format("%d/%d replicas ready", ready, desired);
		return ResourceStatus.builder()
			.kind("Deployment")
			.name(name)
			.namespace(namespace)
			.ready(isReady)
			.message(message)
			.build();
	}

	private ResourceStatus checkReplicaSet(String namespace, String name) throws ApiException {
		AppsV1Api api = new AppsV1Api(apiClient);
		V1ReplicaSet rs = api.readNamespacedReplicaSet(name, namespace).execute();
		int desired = (rs.getSpec().getReplicas() != null) ? rs.getSpec().getReplicas() : 1;
		int ready = nz(rs.getStatus().getReadyReplicas());
		boolean observed = observedCurrent(generationOf(rs.getMetadata()), rs.getStatus().getObservedGeneration());
		boolean isReady = observed && (ready >= desired);
		String message = isReady ? "ready"
				: !observed ? "waiting for rollout" : String.format("%d/%d replicas ready", ready, desired);
		return ResourceStatus.builder()
			.kind("ReplicaSet")
			.name(name)
			.namespace(namespace)
			.ready(isReady)
			.message(message)
			.build();
	}

	private ResourceStatus checkDaemonSet(String namespace, String name) throws ApiException {
		AppsV1Api api = new AppsV1Api(apiClient);
		V1DaemonSet ds = api.readNamespacedDaemonSet(name, namespace).execute();
		int desiredScheduled = nz(ds.getStatus().getDesiredNumberScheduled());
		int updatedScheduled = nz(ds.getStatus().getUpdatedNumberScheduled());
		int numberReady = nz(ds.getStatus().getNumberReady());
		boolean observed = observedCurrent(generationOf(ds.getMetadata()), ds.getStatus().getObservedGeneration());
		boolean isReady = observed && (updatedScheduled >= desiredScheduled) && (numberReady >= desiredScheduled);
		String message = isReady ? "ready"
				: !observed ? "waiting for rollout" : String.format("%d/%d ready", numberReady, desiredScheduled);
		return ResourceStatus.builder()
			.kind("DaemonSet")
			.name(name)
			.namespace(namespace)
			.ready(isReady)
			.message(message)
			.build();
	}

	/** Null-safe count: treats {@code null} as 0. */
	private static int nz(Integer value) {
		return (value != null) ? value : 0;
	}

	/**
	 * Null-safe read of {@code metadata.generation}, returning {@code null} if absent.
	 */
	private static Long generationOf(V1ObjectMeta metadata) {
		return (metadata != null) ? metadata.getGeneration() : null;
	}

	/**
	 * Returns {@code true} when the controller has observed the latest spec, i.e.
	 * {@code status.observedGeneration >= metadata.generation} (both null-safe as 0).
	 * This guards against a mid-rollout false-ready before the controller has reconciled.
	 */
	private static boolean observedCurrent(Long generation, Long observedGeneration) {
		long gen = (generation != null) ? generation : 0L;
		long observed = (observedGeneration != null) ? observedGeneration : 0L;
		return observed >= gen;
	}

	private ResourceStatus checkStatefulSet(String namespace, String name) throws ApiException {
		AppsV1Api api = new AppsV1Api(apiClient);
		V1StatefulSet sts = api.readNamespacedStatefulSet(name, namespace).execute();
		int desired = (sts.getSpec().getReplicas() != null) ? sts.getSpec().getReplicas() : 1;
		int ready = (sts.getStatus().getReadyReplicas() != null) ? sts.getStatus().getReadyReplicas() : 0;
		boolean isReady = ready >= desired;
		String message = isReady ? "ready" : String.format("%d/%d replicas ready", ready, desired);
		return ResourceStatus.builder()
			.kind("StatefulSet")
			.name(name)
			.namespace(namespace)
			.ready(isReady)
			.message(message)
			.build();
	}

	private ResourceStatus checkJob(String namespace, String name) throws ApiException {
		BatchV1Api api = new BatchV1Api(apiClient);
		V1Job job = api.readNamespacedJob(name, namespace).execute();
		int completions = (job.getSpec().getCompletions() != null) ? job.getSpec().getCompletions() : 1;
		int succeeded = (job.getStatus().getSucceeded() != null) ? job.getStatus().getSucceeded() : 0;
		int failed = (job.getStatus().getFailed() != null) ? job.getStatus().getFailed() : 0;
		boolean isReady = succeeded >= completions;
		String message = isReady ? "complete"
				: (failed > 0) ? String.format("failed=%d, succeeded=%d/%d", failed, succeeded, completions)
						: String.format("succeeded=%d/%d", succeeded, completions);
		return ResourceStatus.builder()
			.kind("Job")
			.name(name)
			.namespace(namespace)
			.ready(isReady)
			.message(message)
			.build();
	}

	private ResourceStatus checkPod(String namespace, String name) throws ApiException {
		CoreV1Api api = new CoreV1Api(apiClient);
		V1Pod pod = api.readNamespacedPod(name, namespace).execute();
		boolean isReady = "Running".equals(pod.getStatus().getPhase()) && pod.getStatus().getConditions() != null
				&& pod.getStatus()
					.getConditions()
					.stream()
					.filter((c) -> "Ready".equals(c.getType()))
					.anyMatch((c) -> "True".equals(c.getStatus()));
		String message = isReady ? "ready" : pod.getStatus().getPhase();
		return ResourceStatus.builder()
			.kind("Pod")
			.name(name)
			.namespace(namespace)
			.ready(isReady)
			.message(message)
			.build();
	}

	/**
	 * Polls until all resources in the manifest are ready or the timeout elapses.
	 */
	@Override
	public void waitForReady(String namespace, String manifest, int timeoutSeconds) {
		long deadline = System.currentTimeMillis() + (long) timeoutSeconds * 1000;
		int pollIntervalMs = 5000;

		while (true) {
			List<ResourceStatus> statuses = getResourceStatuses(namespace, manifest);
			boolean allReady = statuses.stream().allMatch(ResourceStatus::isReady);
			if (allReady) {
				return;
			}

			long remaining = deadline - System.currentTimeMillis();
			if (remaining <= 0) {
				break;
			}

			statuses.stream()
				.filter((s) -> !s.isReady())
				.forEach((s) -> log.info("Waiting for {}/{}: {}", s.getKind(), s.getName(), s.getMessage()));

			try {
				Thread.sleep(Math.min(pollIntervalMs, remaining));
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new KubernetesOperationException("Interrupted while waiting for resources to be ready", ex);
			}
		}

		List<ResourceStatus> finalStatuses = getResourceStatuses(namespace, manifest);
		List<ResourceStatus> notReady = finalStatuses.stream().filter((s) -> !s.isReady()).toList();
		if (!notReady.isEmpty()) {
			String msg = notReady.stream()
				.map((s) -> s.getKind() + "/" + s.getName() + ": " + s.getMessage())
				.collect(Collectors.joining(", "));
			throw new WaitTimeoutException("Timeout waiting for resources to be ready: " + msg, notReady);
		}
	}

	private Release decodeRelease(V1Secret secret) throws ReleaseStorageException {
		try {
			byte[] raw = secret.getData().get("release");
			// Helm stores release as: JSON → gzip → base64, then k8s base64-encodes again
			// The k8s client auto-decodes the outer base64, so raw is the inner base64
			byte[] gzipped = Base64.getDecoder().decode(raw);
			byte[] json = gunzip(gzipped);
			return releaseCodec.fromJson(json);
		}
		catch (Exception ex) {
			throw new ReleaseStorageException("Failed to decode release from Secret: " + secret.getMetadata().getName(),
					ex);
		}
	}

	private byte[] encodeRelease(Release release) throws IOException {
		byte[] json = releaseCodec.toJson(release);
		byte[] gzipped = gzip(json);
		String b64 = Base64.getEncoder().encodeToString(gzipped);
		return b64.getBytes(StandardCharsets.UTF_8);
	}

	private byte[] gzip(byte[] data) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
			gz.write(data);
		}
		return bos.toByteArray();
	}

	private byte[] gunzip(byte[] data) throws IOException {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(data); GZIPInputStream gz = new GZIPInputStream(bis)) {
			return gz.readAllBytes();
		}
	}

	private String inferPlural(String kind) {
		if (pluralizer == null) {
			pluralizer = new ResourcePluralizer(apiClient);
		}
		return pluralizer.toPlural(kind);
	}

	private boolean inferNamespaced(String kind) {
		if (pluralizer == null) {
			pluralizer = new ResourcePluralizer(apiClient);
		}
		return pluralizer.isNamespaced(kind);
	}

	/**
	 * Installs a ConfigMap parsed from a YAML string into the given namespace, mimicking
	 * {@code helm install}. If a ConfigMap with the same name already exists (HTTP 409
	 * conflict) it is replaced instead of created.
	 * @param namespace the Kubernetes namespace to install the ConfigMap into
	 * @param yamlContent the YAML representation of the ConfigMap to install
	 * @throws KubernetesOperationException if the Kubernetes API call fails for a reason
	 * other than an already-existing ConfigMap
	 */
	public void installConfigMap(String namespace, String yamlContent) {
		CoreV1Api api = new CoreV1Api(apiClient);
		V1ConfigMap cm = Yaml.loadAs(yamlContent, V1ConfigMap.class);

		try {
			if (log.isInfoEnabled()) {
				log.info("Creating ConfigMap {} in {}", cm.getMetadata().getName(), namespace);
			}
			api.createNamespacedConfigMap(namespace, cm).execute();
		}
		catch (ApiException ex) {
			if (ex.getCode() == 409) { // Conflict / already exists -> replace
				replaceConfigMap(api, namespace, cm);
			}
			else {
				if (log.isDebugEnabled()) {
					log.debug("Error installing ConfigMap: {} / {}", ex.getMessage(), ex.getResponseBody());
				}
				throw new KubernetesOperationException("Failed to install ConfigMap in " + namespace, ex, ex.getCode());
			}
		}
	}

	private void replaceConfigMap(CoreV1Api api, String namespace, V1ConfigMap cm) {
		if (log.isInfoEnabled()) {
			log.info("ConfigMap already exists, replacing");
		}
		try {
			api.replaceNamespacedConfigMap(cm.getMetadata().getName(), namespace, cm).execute();
		}
		catch (ApiException ex) {
			throw new KubernetesOperationException("Failed to replace ConfigMap in " + namespace, ex, ex.getCode());
		}
	}

}
