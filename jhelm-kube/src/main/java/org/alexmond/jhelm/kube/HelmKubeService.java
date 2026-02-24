package org.alexmond.jhelm.kube;

import tools.jackson.databind.json.JsonMapper;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.PatchUtils;
import io.kubernetes.client.util.Yaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.KubeService;
import org.alexmond.jhelm.core.Release;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HelmKubeService implements KubeService {

	private final ApiClient apiClient;

	private final JsonMapper objectMapper = JsonMapper.builder().build();

	/**
	 * Stores a release as a ConfigMap in Kubernetes.
	 */
	public void storeRelease(Release release) throws ApiException {
		CoreV1Api api = new CoreV1Api(apiClient);
		String name = "sh.helm.release.v1." + release.getName() + ".v" + release.getVersion();

		try {
			byte[] releaseJson = objectMapper.writeValueAsBytes(release);
			String encoded = Base64.getEncoder().encodeToString(releaseJson);

			V1ConfigMap configMap = new V1ConfigMap()
				.metadata(new V1ObjectMeta().name(name)
					.namespace(release.getNamespace())
					.putLabelsItem("owner", "helm")
					.putLabelsItem("name", release.getName())
					.putLabelsItem("status", release.getInfo().getStatus())
					.putLabelsItem("version", String.valueOf(release.getVersion())))
				.putDataItem("release", encoded);

			try {
				api.createNamespacedConfigMap(release.getNamespace(), configMap).execute();
			}
			catch (Exception ex) {
				if (ex instanceof ApiException ae && ae.getCode() == 409) {
					api.replaceNamespacedConfigMap(name, release.getNamespace(), configMap).execute();
				}
				else {
					throw ex;
				}
			}
		}
		catch (Exception ex) {
			throw new RuntimeException("Failed to store release", ex);
		}
	}

	/**
	 * Retrieves the latest version of a release from Kubernetes.
	 */
	public Optional<Release> getRelease(String name, String namespace) throws ApiException {
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm,name=" + name;

		V1ConfigMapList list = api.listNamespacedConfigMap(namespace).labelSelector(labelSelector).execute();

		return list.getItems().stream().sorted((s1, s2) -> {
			int v1 = Integer.parseInt(s1.getMetadata().getLabels().get("version"));
			int v2 = Integer.parseInt(s2.getMetadata().getLabels().get("version"));
			return Integer.compare(v2, v1); // Descending
		}).findFirst().map((cm) -> {
			try {
				byte[] decoded = Base64.getDecoder().decode(cm.getData().get("release"));
				return objectMapper.readValue(decoded, Release.class);
			}
			catch (Exception ex) {
				throw new RuntimeException("Failed to decode release", ex);
			}
		});
	}

	/**
	 * Retrieves all releases in a namespace.
	 */
	public List<Release> listReleases(String namespace) throws ApiException {
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm";

		V1ConfigMapList list = api.listNamespacedConfigMap(namespace).labelSelector(labelSelector).execute();

		// Group by name and pick the highest version for each
		Map<String, List<V1ConfigMap>> grouped = list.getItems()
			.stream()
			.collect(Collectors.groupingBy((cm) -> cm.getMetadata().getLabels().get("name")));

		return grouped.values()
			.stream()
			.map((cms) -> cms.stream()
				.max(Comparator.comparingInt((cm) -> Integer.parseInt(cm.getMetadata().getLabels().get("version"))))
				.orElse(null))
			.filter(Objects::nonNull)
			.map((cm) -> {
				try {
					byte[] decoded = Base64.getDecoder().decode(cm.getData().get("release"));
					return objectMapper.readValue(decoded, Release.class);
				}
				catch (Exception ex) {
					log.error("Failed to decode release from ConfigMap {}: {}", cm.getMetadata().getName(),
							ex.getMessage());
					return null;
				}
			})
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
	}

	/**
	 * Retrieves all versions of a specific release.
	 */
	public List<Release> getReleaseHistory(String name, String namespace) throws ApiException {
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm,name=" + name;

		V1ConfigMapList list = api.listNamespacedConfigMap(namespace).labelSelector(labelSelector).execute();

		return list.getItems().stream().sorted((s1, s2) -> {
			int v1 = Integer.parseInt(s1.getMetadata().getLabels().get("version"));
			int v2 = Integer.parseInt(s2.getMetadata().getLabels().get("version"));
			return Integer.compare(v2, v1); // Descending
		}).map((cm) -> {
			try {
				byte[] decoded = Base64.getDecoder().decode(cm.getData().get("release"));
				return objectMapper.readValue(decoded, Release.class);
			}
			catch (Exception ex) {
				throw new RuntimeException("Failed to decode release", ex);
			}
		}).collect(Collectors.toList());
	}

	/**
	 * Deletes all versions of a release from Kubernetes.
	 */
	public void deleteReleaseHistory(String name, String namespace) throws ApiException {
		CoreV1Api api = new CoreV1Api(apiClient);
		String labelSelector = "owner=helm,name=" + name;
		V1ConfigMapList list = api.listNamespacedConfigMap(namespace).labelSelector(labelSelector).execute();
		for (V1ConfigMap cm : list.getItems()) {
			api.deleteNamespacedConfigMap(cm.getMetadata().getName(), namespace).execute();
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
	public void apply(String namespace, String yamlContent) throws ApiException {
		try {
			Iterable<Object> objects = Yaml.loadAll(yamlContent);
			for (Object obj : objects) {
				if (obj instanceof KubernetesObject k8sObj) {
					applyResource(namespace, k8sObj);
				}
			}
		}
		catch (Exception ex) {
			if (ex instanceof ApiException) {
				throw (ApiException) ex;
			}
			throw new RuntimeException("Failed to apply manifest", ex);
		}
	}

	private void applyResource(String namespace, KubernetesObject obj) throws ApiException {
		String apiVersion = obj.getApiVersion();
		String group = apiVersion.contains("/") ? apiVersion.split("/")[0] : "";
		String version = apiVersion.contains("/") ? apiVersion.split("/")[1] : apiVersion;
		String kind = obj.getKind();
		String name = obj.getMetadata().getName();
		String plural = inferPlural(kind);

		log.info("Applying {} ({}/{}) {} in namespace {}", kind, group, version, name, namespace);

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
	public void delete(String namespace, String yamlContent) throws ApiException {
		try {
			Iterable<Object> objects = Yaml.loadAll(yamlContent);
			for (Object obj : objects) {
				if (obj instanceof KubernetesObject k8sObj) {
					deleteResource(namespace, k8sObj);
				}
			}
		}
		catch (Exception ex) {
			if (ex instanceof ApiException) {
				throw (ApiException) ex;
			}
			throw new RuntimeException("Failed to delete manifest", ex);
		}
	}

	private void deleteResource(String namespace, KubernetesObject obj) throws ApiException {
		String group = obj.getApiVersion().contains("/") ? obj.getApiVersion().split("/")[0] : "";
		String version = obj.getApiVersion().contains("/") ? obj.getApiVersion().split("/")[1] : obj.getApiVersion();
		String kind = obj.getKind();
		String name = obj.getMetadata().getName();
		String plural = inferPlural(kind);

		log.info("Deleting {} {} in namespace {}", kind, name, namespace);

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

	private String inferPlural(String kind) {
		// Very basic pluralization logic. In real Helm/Kubectl, this is done via
		// discovery.
		String plural = kind.toLowerCase() + "s";
		if (kind.endsWith("y")) {
			plural = kind.toLowerCase().substring(0, kind.length() - 1) + "ies";
		}
		return plural;
	}

	/**
	 * Mimics "helm install" by applying a ConfigMap from a YAML string.
	 */
	public void installConfigMap(String namespace, String yamlContent) throws ApiException {
		CoreV1Api api = new CoreV1Api(apiClient);
		V1ConfigMap cm = Yaml.loadAs(yamlContent, V1ConfigMap.class);

		try {
			log.info("Creating ConfigMap {} in {}", cm.getMetadata().getName(), namespace);
			api.createNamespacedConfigMap(namespace, cm).execute();
		}
		catch (Exception ex) {
			if (ex instanceof ApiException ae && ae.getCode() == 409) { // Conflict/Already
																		// exists
				log.info("ConfigMap already exists, replacing");
				api.replaceNamespacedConfigMap(cm.getMetadata().getName(), namespace, cm).execute();
			}
			else {
				log.error("Error installing ConfigMap: {}", ex.getMessage());
				if (ex instanceof ApiException ae) {
					log.error("API Response: {}", ae.getResponseBody());
				}
				throw ex;
			}
		}
	}

}
