package org.alexmond.jhelm.kube.service.internal;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.openapi.models.VersionInfo;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.gotemplate.helm.functions.KubernetesProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link KubernetesProvider} implementation backed by the official Kubernetes Java
 * client. It provides real cluster access for template functions such as {@code lookup}
 * and {@code Capabilities}, gracefully degrading to empty results or stub version
 * information when the API server is unreachable.
 */
@Slf4j
public class KubernetesClientProvider implements KubernetesProvider {

	private final ApiClient apiClient;

	private final Gson gson = JSON.getGson();

	private final CoreV1Api coreV1Api;

	private final AppsV1Api appsV1Api;

	private final VersionApi versionApi;

	private volatile Boolean available;

	/**
	 * Creates a provider backed by the given {@link KubeClient}.
	 * @param kubeClient the jhelm Kubernetes client wrapper holding the configured API
	 * client used to look up resources and query cluster version information
	 */
	public KubernetesClientProvider(KubeClient kubeClient) {
		ApiClient apiClient = kubeClient.apiClient();
		this.apiClient = apiClient;
		this.coreV1Api = new CoreV1Api(apiClient);
		this.appsV1Api = new AppsV1Api(apiClient);
		this.versionApi = new VersionApi(apiClient);
	}

	@Override
	public Map<String, Object> lookup(String apiVersion, String kind, String namespace, String name) {
		if (!isAvailable()) {
			if (log.isWarnEnabled()) {
				log.warn("Kubernetes API not available, returning empty result for lookup");
			}
			return Map.of();
		}

		try {
			if (log.isDebugEnabled()) {
				log.debug("Looking up Kubernetes resource: apiVersion={}, kind={}, namespace={}, name={}", apiVersion,
						kind, namespace, name);
			}

			Object resource = fetchResource(apiVersion, kind, namespace, name);
			if (resource == null) {
				return Map.of();
			}

			return convertToMap(resource);

		}
		catch (ApiException ex) {
			if (ex.getCode() == 404) {
				if (log.isDebugEnabled()) {
					log.debug("Resource not found: {}/{} in namespace {}", apiVersion, kind, namespace);
				}
				return Map.of();
			}
			if (log.isErrorEnabled()) {
				log.error("Error looking up Kubernetes resource: {}", ex.getMessage(), ex);
			}
			return Map.of();
		}
		catch (Exception ex) {
			if (log.isErrorEnabled()) {
				log.error("Unexpected error during Kubernetes lookup: {}", ex.getMessage(), ex);
			}
			return Map.of();
		}
	}

	@Override
	public Map<String, Object> getVersion() {
		if (!isAvailable()) {
			if (log.isWarnEnabled()) {
				log.warn("Kubernetes API not available, returning stub version info");
			}
			return getStubVersion();
		}

		try {
			VersionInfo versionInfo = versionApi.getCode().execute();
			Map<String, Object> version = new LinkedHashMap<>();
			version.put("Major", versionInfo.getMajor());
			version.put("Minor", versionInfo.getMinor());
			version.put("GitVersion", versionInfo.getGitVersion());
			version.put("GitCommit", versionInfo.getGitCommit());
			version.put("Platform", versionInfo.getPlatform());
			version.put("BuildDate", versionInfo.getBuildDate());
			return version;
		}
		catch (ApiException ex) {
			if (log.isErrorEnabled()) {
				log.error("Error fetching Kubernetes version: {}", ex.getMessage(), ex);
			}
			return getStubVersion();
		}
	}

	@Override
	public boolean isAvailable() {
		if (available != null) {
			return available;
		}

		try {
			// Try to get version as a health check
			versionApi.getCode().execute();
			available = true;
			if (log.isInfoEnabled()) {
				log.info("Kubernetes API is available");
			}
			return true;
		}
		catch (Exception ex) {
			available = false;
			if (log.isWarnEnabled()) {
				log.warn("Kubernetes API is not available: {}", ex.getMessage());
			}
			return false;
		}
	}

	/**
	 * Fetch a resource from Kubernetes based on apiVersion and kind
	 */
	private Object fetchResource(String apiVersion, String kind, String namespace, String name) throws ApiException {
		// Handle empty namespace for cluster-scoped resources
		if (namespace == null || namespace.isEmpty()) {
			namespace = "default";
		}

		// Core v1 resources
		if ("v1".equals(apiVersion)) {
			return fetchCoreV1Resource(kind, namespace, name);
		}

		// Apps/v1 resources
		if ("apps/v1".equals(apiVersion)) {
			return fetchAppsV1Resource(kind, namespace, name);
		}

		if (log.isWarnEnabled()) {
			log.warn("Unsupported apiVersion: {}", apiVersion);
		}
		return null;
	}

	/**
	 * Fetch Core v1 API resources (Pod, Service, ConfigMap, Secret, etc.)
	 */
	private Object fetchCoreV1Resource(String kind, String namespace, String name) throws ApiException {
		// Each API method returns a fluent request builder; .execute() performs the call
		// and
		// returns the actual model (the resource or a *List). Without it, lookup would
		// serialize the request builder instead of the resource (no data/spec/status).
		return switch (kind) {
			case "Pod" -> name.isEmpty() ? coreV1Api.listNamespacedPod(namespace).execute()
					: coreV1Api.readNamespacedPod(name, namespace).execute();

			case "Service" -> name.isEmpty() ? coreV1Api.listNamespacedService(namespace).execute()
					: coreV1Api.readNamespacedService(name, namespace).execute();

			case "ConfigMap" -> name.isEmpty() ? coreV1Api.listNamespacedConfigMap(namespace).execute()
					: coreV1Api.readNamespacedConfigMap(name, namespace).execute();

			case "Secret" -> name.isEmpty() ? coreV1Api.listNamespacedSecret(namespace).execute()
					: coreV1Api.readNamespacedSecret(name, namespace).execute();

			case "Namespace" ->
				name.isEmpty() ? coreV1Api.listNamespace().execute() : coreV1Api.readNamespace(name).execute();

			case "PersistentVolumeClaim" ->
				name.isEmpty() ? coreV1Api.listNamespacedPersistentVolumeClaim(namespace).execute()
						: coreV1Api.readNamespacedPersistentVolumeClaim(name, namespace).execute();

			case "ServiceAccount" -> name.isEmpty() ? coreV1Api.listNamespacedServiceAccount(namespace).execute()
					: coreV1Api.readNamespacedServiceAccount(name, namespace).execute();

			default -> {
				if (log.isWarnEnabled()) {
					log.warn("Unsupported Core v1 kind: {}", kind);
				}
				yield null;
			}
		};
	}

	/**
	 * Fetch Apps/v1 API resources (Deployment, StatefulSet, DaemonSet, ReplicaSet)
	 */
	private Object fetchAppsV1Resource(String kind, String namespace, String name) throws ApiException {
		// .execute() performs the call and returns the model; see fetchCoreV1Resource.
		return switch (kind) {
			case "Deployment" -> name.isEmpty() ? appsV1Api.listNamespacedDeployment(namespace).execute()
					: appsV1Api.readNamespacedDeployment(name, namespace).execute();

			case "StatefulSet" -> name.isEmpty() ? appsV1Api.listNamespacedStatefulSet(namespace).execute()
					: appsV1Api.readNamespacedStatefulSet(name, namespace).execute();

			case "DaemonSet" -> name.isEmpty() ? appsV1Api.listNamespacedDaemonSet(namespace).execute()
					: appsV1Api.readNamespacedDaemonSet(name, namespace).execute();

			case "ReplicaSet" -> name.isEmpty() ? appsV1Api.listNamespacedReplicaSet(namespace).execute()
					: appsV1Api.readNamespacedReplicaSet(name, namespace).execute();

			default -> {
				if (log.isWarnEnabled()) {
					log.warn("Unsupported Apps v1 kind: {}", kind);
				}
				yield null;
			}
		};
	}

	/**
	 * Convert a Kubernetes resource object to a nested {@link Map} for template usage,
	 * mirroring Helm's {@code lookup}: the <em>entire</em> object is returned (metadata,
	 * data, stringData, spec, status, ...), not just its metadata. Serialization goes
	 * through the Kubernetes client's own Gson, so field names match the wire format and
	 * {@code Secret.data} values are rendered as base64 strings — exactly what a chart's
	 * {@code index $obj.data "key" | b64dec} password-retention idiom expects.
	 * @param resource the fetched client model (single object or a {@code *List})
	 * @return the resource as a nested map of maps/lists/scalars
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> convertToMap(Object resource) {
		if (resource == null) {
			return Map.of();
		}

		// If it's already a Map, return it
		if (resource instanceof Map) {
			return (Map<String, Object>) resource;
		}

		try {
			Object converted = jsonToJava(this.gson.toJsonTree(resource));
			if (converted instanceof Map) {
				return (Map<String, Object>) converted;
			}
			// A non-object top level is unexpected for a Kubernetes model; wrap it so the
			// caller still gets a map.
			Map<String, Object> wrapper = new LinkedHashMap<>();
			wrapper.put("value", converted);
			return wrapper;
		}
		catch (Exception ex) {
			if (log.isWarnEnabled()) {
				log.warn("Could not serialize looked-up resource: {}", ex.getMessage());
			}
			return Map.of();
		}
	}

	/**
	 * Recursively converts a Gson {@link JsonElement} into plain Java maps, lists and
	 * scalars that the template engine can traverse.
	 * @param element the JSON element to convert
	 * @return a {@link Map}, {@link List}, {@link Boolean}, {@link Long}, {@link Double},
	 * {@link String} or {@code null}
	 */
	static Object jsonToJava(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (element.isJsonObject()) {
			Map<String, Object> map = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				map.put(entry.getKey(), jsonToJava(entry.getValue()));
			}
			return map;
		}
		if (element.isJsonArray()) {
			List<Object> list = new ArrayList<>();
			for (JsonElement item : element.getAsJsonArray()) {
				list.add(jsonToJava(item));
			}
			return list;
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (primitive.isBoolean()) {
			return primitive.getAsBoolean();
		}
		if (primitive.isNumber()) {
			return numberToJava(primitive.getAsString());
		}
		return primitive.getAsString();
	}

	/**
	 * Parses a JSON number's raw text into a {@link Long} when integral, otherwise a
	 * {@link Double}, so template arithmetic and comparisons behave sensibly.
	 * @param raw the number's textual form
	 * @return a {@link Long} or {@link Double}
	 */
	static Object numberToJava(String raw) {
		if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
			return Double.valueOf(raw);
		}
		try {
			return Long.valueOf(raw);
		}
		catch (NumberFormatException ex) {
			return Double.valueOf(raw);
		}
	}

	/**
	 * Return stub version when Kubernetes API is not available. Kept aligned with the
	 * engine's default {@code .Capabilities.KubeVersion} ({@code v1.35.0}) so the
	 * {@code kubeVersion} template function and {@code .Capabilities.KubeVersion} agree
	 * when no live cluster is reachable.
	 */
	private Map<String, Object> getStubVersion() {
		Map<String, Object> version = new HashMap<>();
		version.put("Major", "1");
		version.put("Minor", "35");
		version.put("GitVersion", "v1.35.0");
		version.put("GitCommit", "unknown");
		version.put("Platform", "linux/amd64");
		return version;
	}

}
