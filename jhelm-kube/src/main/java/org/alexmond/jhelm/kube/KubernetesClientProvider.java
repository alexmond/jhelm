package org.alexmond.jhelm.kube;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.VersionInfo;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.gotemplate.helm.kubernetes.KubernetesProvider;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation of KubernetesProvider using Kubernetes Java client.
 * Provides real Kubernetes API access for template functions.
 */
@Slf4j
public class KubernetesClientProvider implements KubernetesProvider {

    private final ApiClient apiClient;
    private final CoreV1Api coreV1Api;
    private final AppsV1Api appsV1Api;
    private final VersionApi versionApi;
    private volatile Boolean available;

    public KubernetesClientProvider(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.coreV1Api = new CoreV1Api(apiClient);
        this.appsV1Api = new AppsV1Api(apiClient);
        this.versionApi = new VersionApi(apiClient);
    }

    @Override
    public Map<String, Object> lookup(String apiVersion, String kind, String namespace, String name) {
        if (!isAvailable()) {
            log.warn("Kubernetes API not available, returning empty result for lookup");
            return Map.of();
        }

        try {
            log.debug("Looking up Kubernetes resource: apiVersion={}, kind={}, namespace={}, name={}",
                    apiVersion, kind, namespace, name);

            Object resource = fetchResource(apiVersion, kind, namespace, name);
            if (resource == null) {
                return Map.of();
            }

            return convertToMap(resource);

        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.debug("Resource not found: {}/{} in namespace {}", apiVersion, kind, namespace);
                return Map.of();
            }
            log.error("Error looking up Kubernetes resource: {}", e.getMessage(), e);
            return Map.of();
        } catch (Exception e) {
            log.error("Unexpected error during Kubernetes lookup: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> getVersion() {
        if (!isAvailable()) {
            log.warn("Kubernetes API not available, returning stub version info");
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
        } catch (ApiException e) {
            log.error("Error fetching Kubernetes version: {}", e.getMessage(), e);
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
            log.info("Kubernetes API is available");
            return true;
        } catch (Exception e) {
            available = false;
            log.warn("Kubernetes API is not available: {}", e.getMessage());
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

        log.warn("Unsupported apiVersion: {}", apiVersion);
        return null;
    }

    /**
     * Fetch Core v1 API resources (Pod, Service, ConfigMap, Secret, etc.)
     */
    private Object fetchCoreV1Resource(String kind, String namespace, String name) throws ApiException {
        return switch (kind) {
            case "Pod" -> name.isEmpty()
                    ? coreV1Api.listNamespacedPod(namespace)
                    : coreV1Api.readNamespacedPod(name, namespace);

            case "Service" -> name.isEmpty()
                    ? coreV1Api.listNamespacedService(namespace)
                    : coreV1Api.readNamespacedService(name, namespace);

            case "ConfigMap" -> name.isEmpty()
                    ? coreV1Api.listNamespacedConfigMap(namespace)
                    : coreV1Api.readNamespacedConfigMap(name, namespace);

            case "Secret" -> name.isEmpty()
                    ? coreV1Api.listNamespacedSecret(namespace)
                    : coreV1Api.readNamespacedSecret(name, namespace);

            case "Namespace" -> name.isEmpty()
                    ? coreV1Api.listNamespace()
                    : coreV1Api.readNamespace(name);

            case "PersistentVolumeClaim" -> name.isEmpty()
                    ? coreV1Api.listNamespacedPersistentVolumeClaim(namespace)
                    : coreV1Api.readNamespacedPersistentVolumeClaim(name, namespace);

            case "ServiceAccount" -> name.isEmpty()
                    ? coreV1Api.listNamespacedServiceAccount(namespace)
                    : coreV1Api.readNamespacedServiceAccount(name, namespace);

            default -> {
                log.warn("Unsupported Core v1 kind: {}", kind);
                yield null;
            }
        };
    }

    /**
     * Fetch Apps/v1 API resources (Deployment, StatefulSet, DaemonSet, ReplicaSet)
     */
    private Object fetchAppsV1Resource(String kind, String namespace, String name) throws ApiException {
        return switch (kind) {
            case "Deployment" -> name.isEmpty()
                    ? appsV1Api.listNamespacedDeployment(namespace)
                    : appsV1Api.readNamespacedDeployment(name, namespace);

            case "StatefulSet" -> name.isEmpty()
                    ? appsV1Api.listNamespacedStatefulSet(namespace)
                    : appsV1Api.readNamespacedStatefulSet(name, namespace);

            case "DaemonSet" -> name.isEmpty()
                    ? appsV1Api.listNamespacedDaemonSet(namespace)
                    : appsV1Api.readNamespacedDaemonSet(name, namespace);

            case "ReplicaSet" -> name.isEmpty()
                    ? appsV1Api.listNamespacedReplicaSet(namespace)
                    : appsV1Api.readNamespacedReplicaSet(name, namespace);

            default -> {
                log.warn("Unsupported Apps v1 kind: {}", kind);
                yield null;
            }
        };
    }

    /**
     * Convert Kubernetes resource object to Map for template usage
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

        // For Kubernetes objects, extract common fields
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // For resources with metadata
            var metadataMethod = resource.getClass().getMethod("getMetadata");
            if (metadataMethod != null) {
                V1ObjectMeta metadata = (V1ObjectMeta) metadataMethod.invoke(resource);
                if (metadata != null) {
                    result.put("metadata", createMetadataMap(metadata));
                }
            }

            // For list results
            if (resource.getClass().getSimpleName().contains("List")) {
                var items = resource.getClass().getMethod("getItems").invoke(resource);
                result.put("items", items);
            }

        } catch (Exception e) {
            log.warn("Could not extract metadata from resource: {}", e.getMessage());
            // Return a simple string representation
            result.put("value", resource.toString());
        }

        return result;
    }

    /**
     * Create a Map from Kubernetes ObjectMeta
     */
    private Map<String, Object> createMetadataMap(V1ObjectMeta metadata) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (metadata.getName() != null) map.put("name", metadata.getName());
        if (metadata.getNamespace() != null) map.put("namespace", metadata.getNamespace());
        if (metadata.getLabels() != null) map.put("labels", metadata.getLabels());
        if (metadata.getAnnotations() != null) map.put("annotations", metadata.getAnnotations());
        if (metadata.getUid() != null) map.put("uid", metadata.getUid());
        if (metadata.getResourceVersion() != null) map.put("resourceVersion", metadata.getResourceVersion());
        if (metadata.getCreationTimestamp() != null)
            map.put("creationTimestamp", metadata.getCreationTimestamp().toString());
        return map;
    }

    /**
     * Return stub version when Kubernetes API is not available
     */
    private Map<String, Object> getStubVersion() {
        Map<String, Object> version = new HashMap<>();
        version.put("Major", "1");
        version.put("Minor", "28");
        version.put("GitVersion", "v1.28.0");
        version.put("GitCommit", "unknown");
        version.put("Platform", "linux/amd64");
        return version;
    }
}
