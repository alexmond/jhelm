package org.alexmond.jhelm.kube.service;

import tools.jackson.databind.json.JsonMapper;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.util.PatchUtils;
import io.kubernetes.client.util.Yaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.KubernetesOperationException;
import org.alexmond.jhelm.core.exception.ReleaseStorageException;
import org.alexmond.jhelm.core.exception.WaitTimeoutException;
import org.alexmond.jhelm.core.service.KubeService;
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

@RequiredArgsConstructor
@Slf4j
public class HelmKubeService implements KubeService {

	private final ApiClient apiClient;

	private final JsonMapper objectMapper = JsonMapper.builder().build();

	private ResourcePluralizer pluralizer;

	/**
	 * Stores a release as a Secret in Kubernetes, matching Helm's storage format.
	 */
	@Override
	public void storeRelease(Release release) throws ReleaseStorageException {
		CoreV1Api api = new CoreV1Api(apiClient);
		String name = "sh.helm.release.v1." + release.getName() + ".v" + release.getVersion();

		try {
			byte[] encoded = encodeRelease(release);

			V1Secret secret = new V1Secret()
				.metadata(new V1ObjectMeta().name(name)
					.namespace(release.getNamespace())
					.putLabelsItem("owner", "helm")
					.putLabelsItem("name", release.getName())
					.putLabelsItem("status", release.getInfo().getStatus())
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
	public Optional<Release> getRelease(String name, String namespace) throws Exception {
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm,name=" + name;

		V1SecretList list = api.listNamespacedSecret(namespace).labelSelector(labelSelector).execute();

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
	public List<Release> listReleases(String namespace) throws Exception {
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm";

		V1SecretList list = api.listNamespacedSecret(namespace).labelSelector(labelSelector).execute();

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
	public List<Release> getReleaseHistory(String name, String namespace) throws Exception {
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm,name=" + name;

		V1SecretList list = api.listNamespacedSecret(namespace).labelSelector(labelSelector).execute();

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
	public void deleteReleaseHistory(String name, String namespace) throws ApiException {
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm,name=" + name;
		V1SecretList list = api.listNamespacedSecret(namespace).labelSelector(labelSelector).execute();
		for (V1Secret secret : list.getItems()) {
			api.deleteNamespacedSecret(secret.getMetadata().getName(), namespace).execute();
		}
	}

	/**
	 * Mimics basic "helm status" or pod listing in a namespace.
	 */
	public List<String> listPods(String namespace) throws ApiException {
		CoreV1Api api = new CoreV1Api(apiClient);
		V1PodList list = api.listNamespacedPod(namespace).execute();

		return list.getItems().stream().map((pod) -> pod.getMetadata().getName()).collect(Collectors.toList());
	}

	/**
	 * Applies a YAML manifest which can contain multiple resources.
	 */
	@Override
	public void apply(String namespace, String yamlContent) throws Exception {
		try {
			Iterable<Object> objects = Yaml.loadAll(yamlContent);
			for (Object obj : objects) {
				if (obj instanceof KubernetesObject k8sObj) {
					applyResource(namespace, k8sObj);
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

	private void applyResource(String namespace, KubernetesObject obj) throws ApiException {
		String apiVersion = obj.getApiVersion();
		String group = apiVersion.contains("/") ? apiVersion.split("/")[0] : "";
		String version = apiVersion.contains("/") ? apiVersion.split("/")[1] : apiVersion;
		String kind = obj.getKind();
		String name = obj.getMetadata().getName();
		String plural = inferPlural(kind);

		if (log.isInfoEnabled()) {
			log.info("Applying {} ({}/{}) {} in namespace {}", kind, group, version, name, namespace);
		}

		V1Patch patch = new V1Patch(Yaml.dump(obj));
		CustomObjectsApi api = new CustomObjectsApi(apiClient);

		PatchUtils.patch(Object.class, () -> {
			if (namespace != null && !namespace.isEmpty()) {
				return api.patchNamespacedCustomObject(group, version, namespace, plural, name, patch)
					.fieldManager("helm")
					.force(true)
					.buildCall(null);
			}
			return api.patchClusterCustomObject(group, version, plural, name, patch)
				.fieldManager("helm")
				.force(true)
				.buildCall(null);
		}, V1Patch.PATCH_FORMAT_APPLY_YAML, apiClient);
	}

	/**
	 * Deletes resources in a YAML manifest.
	 */
	@Override
	public void delete(String namespace, String yamlContent) throws Exception {
		try {
			Iterable<Object> objects = Yaml.loadAll(yamlContent);
			for (Object obj : objects) {
				if (obj instanceof KubernetesObject k8sObj) {
					deleteResource(namespace, k8sObj);
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

	private void deleteResource(String namespace, KubernetesObject obj) throws ApiException {
		String group = obj.getApiVersion().contains("/") ? obj.getApiVersion().split("/")[0] : "";
		String version = obj.getApiVersion().contains("/") ? obj.getApiVersion().split("/")[1] : obj.getApiVersion();
		String kind = obj.getKind();
		String name = obj.getMetadata().getName();
		String plural = inferPlural(kind);

		if (log.isInfoEnabled()) {
			log.info("Deleting {} {} in namespace {}", kind, name, namespace);
		}

		CustomObjectsApi api = new CustomObjectsApi(apiClient);
		try {
			if (namespace != null && !namespace.isEmpty()) {
				api.deleteNamespacedCustomObject(group, version, namespace, plural, name).execute();
			}
			else {
				api.deleteClusterCustomObject(group, version, plural, name).execute();
			}
		}
		catch (Exception ex) {
			if (!(ex instanceof ApiException ae && ae.getCode() == 404)) {
				throw ex;
			}
		}
	}

	/**
	 * Returns the readiness status for each resource in the rendered manifest.
	 */
	@Override
	public List<ResourceStatus> getResourceStatuses(String namespace, String manifest) throws Exception {
		List<ResourceStatus> statuses = new ArrayList<>();
		Iterable<Object> objects = Yaml.loadAll(manifest);
		for (Object obj : objects) {
			if (obj instanceof KubernetesObject k8sObj) {
				statuses.add(checkResourceStatus(namespace, k8sObj));
			}
		}
		return statuses;
	}

	private ResourceStatus checkResourceStatus(String namespace, KubernetesObject obj) {
		String kind = obj.getKind();
		String name = obj.getMetadata().getName();
		try {
			return switch (kind) {
				case "Deployment" -> checkDeployment(namespace, name);
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
		int ready = (dep.getStatus().getReadyReplicas() != null) ? dep.getStatus().getReadyReplicas() : 0;
		int updated = (dep.getStatus().getUpdatedReplicas() != null) ? dep.getStatus().getUpdatedReplicas() : 0;
		boolean isReady = (ready >= desired) && (updated >= desired);
		String message = isReady ? "ready" : String.format("%d/%d replicas ready", ready, desired);
		return ResourceStatus.builder()
			.kind("Deployment")
			.name(name)
			.namespace(namespace)
			.ready(isReady)
			.message(message)
			.build();
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
	public void waitForReady(String namespace, String manifest, int timeoutSeconds) throws Exception {
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

			Thread.sleep(Math.min(pollIntervalMs, remaining));
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
			return objectMapper.readValue(json, Release.class);
		}
		catch (Exception ex) {
			throw new ReleaseStorageException("Failed to decode release from Secret: " + secret.getMetadata().getName(),
					ex);
		}
	}

	private byte[] encodeRelease(Release release) throws Exception {
		byte[] json = objectMapper.writeValueAsBytes(release);
		byte[] gzipped = gzip(json);
		String b64 = Base64.getEncoder().encodeToString(gzipped);
		return b64.getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	private byte[] gzip(byte[] data) throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
			gz.write(data);
		}
		return bos.toByteArray();
	}

	private byte[] gunzip(byte[] data) throws Exception {
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

	/**
	 * Mimics "helm install" by applying a ConfigMap from a YAML string.
	 */
	public void installConfigMap(String namespace, String yamlContent) throws ApiException {
		CoreV1Api api = new CoreV1Api(apiClient);
		V1ConfigMap cm = Yaml.loadAs(yamlContent, V1ConfigMap.class);

		try {
			if (log.isInfoEnabled()) {
				log.info("Creating ConfigMap {} in {}", cm.getMetadata().getName(), namespace);
			}
			api.createNamespacedConfigMap(namespace, cm).execute();
		}
		catch (Exception ex) {
			if (ex instanceof ApiException ae && ae.getCode() == 409) { // Conflict/Already
																		// exists
				if (log.isInfoEnabled()) {
					log.info("ConfigMap already exists, replacing");
				}
				api.replaceNamespacedConfigMap(cm.getMetadata().getName(), namespace, cm).execute();
			}
			else {
				if (log.isDebugEnabled()) {
					log.debug("Error installing ConfigMap: {}", ex.getMessage());
				}
				if (ex instanceof ApiException ae) {
					if (log.isDebugEnabled()) {
						log.debug("API Response: {}", ae.getResponseBody());
					}
				}
				throw ex;
			}
		}
	}

}
