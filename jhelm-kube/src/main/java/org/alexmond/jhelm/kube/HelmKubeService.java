package org.alexmond.jhelm.kube;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Yaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.Release;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HelmKubeService {

    private final ApiClient apiClient;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

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
                    .metadata(new V1ObjectMeta()
                            .name(name)
                            .namespace(release.getNamespace())
                            .putLabelsItem("owner", "helm")
                            .putLabelsItem("name", release.getName())
                            .putLabelsItem("status", release.getInfo().getStatus())
                            .putLabelsItem("version", String.valueOf(release.getVersion())))
                    .putDataItem("release", encoded);

            try {
                api.createNamespacedConfigMap(release.getNamespace(), configMap).execute();
            } catch (Exception e) {
                if (e instanceof ApiException ae && ae.getCode() == 409) {
                    api.replaceNamespacedConfigMap(name, release.getNamespace(), configMap).execute();
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to store release", e);
        }
    }

    /**
     * Retrieves the latest version of a release from Kubernetes.
     */
    public Optional<Release> getRelease(String name, String namespace) throws ApiException {
        CoreV1Api api = new CoreV1Api(apiClient);
        String labelSelector = "owner=helm,name=" + name;
        
        V1ConfigMapList list = api.listNamespacedConfigMap(namespace).labelSelector(labelSelector).execute();
        
        return list.getItems().stream()
                .sorted((s1, s2) -> {
                    int v1 = Integer.parseInt(s1.getMetadata().getLabels().get("version"));
                    int v2 = Integer.parseInt(s2.getMetadata().getLabels().get("version"));
                    return Integer.compare(v2, v1); // Descending
                })
                .findFirst()
                .map(cm -> {
                    try {
                        byte[] decoded = Base64.getDecoder().decode(cm.getData().get("release"));
                        return objectMapper.readValue(decoded, Release.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to decode release", e);
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
        Map<String, List<V1ConfigMap>> grouped = list.getItems().stream()
                .collect(Collectors.groupingBy(cm -> cm.getMetadata().getLabels().get("name")));

        return grouped.values().stream()
                .map(cms -> cms.stream()
                        .max(Comparator.comparingInt(cm -> Integer.parseInt(cm.getMetadata().getLabels().get("version"))))
                        .orElse(null))
                .filter(Objects::nonNull)
                .map(cm -> {
                    try {
                        byte[] decoded = Base64.getDecoder().decode(cm.getData().get("release"));
                        return objectMapper.readValue(decoded, Release.class);
                    } catch (Exception e) {
                        log.error("Failed to decode release from ConfigMap {}: {}", cm.getMetadata().getName(), e.getMessage());
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

        return list.getItems().stream()
                .sorted((s1, s2) -> {
                    int v1 = Integer.parseInt(s1.getMetadata().getLabels().get("version"));
                    int v2 = Integer.parseInt(s2.getMetadata().getLabels().get("version"));
                    return Integer.compare(v2, v1); // Descending
                })
                .map(cm -> {
                    try {
                        byte[] decoded = Base64.getDecoder().decode(cm.getData().get("release"));
                        return objectMapper.readValue(decoded, Release.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to decode release", e);
                    }
                })
                .collect(Collectors.toList());
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
        
        return list.getItems().stream()
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.toList());
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
        } catch (Exception e) {
            if (e instanceof ApiException) throw (ApiException) e;
            throw new RuntimeException("Failed to apply manifest", e);
        }
    }

    private void applyResource(String namespace, KubernetesObject obj) throws ApiException {
        String apiVersion = obj.getApiVersion();
        String group = apiVersion.contains("/") ? apiVersion.split("/")[0] : "";
        String version = apiVersion.contains("/") ? apiVersion.split("/")[1] : apiVersion;
        String kind = obj.getKind();
        String name = obj.getMetadata().getName();
        String plural = inferPlural(kind);

        log.info("Applying {} {} in namespace {}", kind, name, namespace);
        System.out.println("[DEBUG_LOG] Applying " + kind + " (" + group + "/" + version + ") " + name + " in namespace " + namespace);

        if (group.isEmpty() && version.equals("v1")) {
            applyCoreResource(namespace, kind, name, obj);
            return;
        }

        // We use CustomObjectsApi for generic resource handling
        CustomObjectsApi api = new CustomObjectsApi(apiClient);

        try {
            if (namespace != null && !namespace.isEmpty()) {
                api.getNamespacedCustomObject(group, version, namespace, plural, name);
                api.replaceNamespacedCustomObject(group, version, namespace, plural, name, obj);
            } else {
                api.getClusterCustomObject(group, version, plural, name).execute();
                api.replaceClusterCustomObject(group, version, plural, name, obj).execute();
            }
        } catch (Exception e) {
            if (e instanceof ApiException ae && ae.getCode() == 404) {
                try {
                    if (namespace != null && !namespace.isEmpty()) {
                        api.createNamespacedCustomObject(group, version, namespace, plural, obj);
                    } else {
                        api.createClusterCustomObject(group, version, plural, obj).execute();
                    }
                } catch (Exception ce) {
                    System.err.println("[DEBUG_LOG] Create failed for " + kind + ": " + (ce instanceof ApiException cae ? cae.getResponseBody() : ce.getMessage()));
                    throw ce;
                }
            } else {
                System.err.println("[DEBUG_LOG] Apply failed for " + kind + ": " + (e instanceof ApiException ae2 ? ae2.getResponseBody() : e.getMessage()));
                throw e;
            }
        }
    }

    private void applyCoreResource(String namespace, String kind, String name, KubernetesObject obj) throws ApiException {
        CoreV1Api api = new CoreV1Api(apiClient);
        String yaml = Yaml.dump(obj);
        
        switch (kind) {
            case "ConfigMap" -> {
                V1ConfigMap cm = Yaml.loadAs(yaml, V1ConfigMap.class);
                try {
                    api.readNamespacedConfigMap(name, namespace).execute();
                    api.replaceNamespacedConfigMap(name, namespace, cm).execute();
                } catch (Exception e) {
                    if (e instanceof ApiException ae && ae.getCode() == 404) {
                        api.createNamespacedConfigMap(namespace, cm).execute();
                    } else throw e;
                }
            }
            case "Service" -> {
                io.kubernetes.client.openapi.models.V1Service svc = Yaml.loadAs(yaml, io.kubernetes.client.openapi.models.V1Service.class);
                try {
                    api.readNamespacedService(name, namespace).execute();
                    api.replaceNamespacedService(name, namespace, svc).execute();
                } catch (Exception e) {
                    if (e instanceof ApiException ae && ae.getCode() == 404) {
                        api.createNamespacedService(namespace, svc).execute();
                    } else throw e;
                }
            }
            case "Secret" -> {
                V1Secret secret = Yaml.loadAs(yaml, V1Secret.class);
                try {
                    api.readNamespacedSecret(name, namespace).execute();
                    api.replaceNamespacedSecret(name, namespace, secret).execute();
                } catch (Exception e) {
                    if (e instanceof ApiException ae && ae.getCode() == 404) {
                        api.createNamespacedSecret(namespace, secret).execute();
                    } else throw e;
                }
            }
            default -> {
                // Fallback to CustomObjectsApi if not explicitly handled
                CustomObjectsApi coa = new CustomObjectsApi(apiClient);
                String plural = inferPlural(kind);
                try {
                    coa.getNamespacedCustomObject("", "v1", namespace, plural, name).execute();
                    coa.replaceNamespacedCustomObject("", "v1", namespace, plural, name, obj).execute();
                } catch (Exception e) {
                    if (e instanceof ApiException ae && ae.getCode() == 404) {
                        coa.createNamespacedCustomObject("", "v1", namespace, plural, obj).execute();
                    } else throw e;
                }
            }
        }
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
        } catch (Exception e) {
            if (e instanceof ApiException) throw (ApiException) e;
            throw new RuntimeException("Failed to delete manifest", e);
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
            } else {
                api.deleteClusterCustomObject(group, version, plural, name).execute();
            }
        } catch (Exception e) {
            if (!(e instanceof ApiException ae && ae.getCode() == 404)) {
                throw e;
            }
        }
    }

    private String inferPlural(String kind) {
        // Very basic pluralization logic. In real Helm/Kubectl, this is done via discovery.
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
            System.out.println("[DEBUG_LOG] Creating ConfigMap " + cm.getMetadata().getName() + " in " + namespace);
            api.createNamespacedConfigMap(namespace, cm).execute();
        } catch (Exception e) {
            if (e instanceof ApiException ae && ae.getCode() == 409) { // Conflict/Already exists
                System.out.println("[DEBUG_LOG] ConfigMap already exists, replacing");
                api.replaceNamespacedConfigMap(cm.getMetadata().getName(), namespace, cm).execute();
            } else {
                System.err.println("[DEBUG_LOG] Error installing ConfigMap: " + e.getMessage());
                if (e instanceof ApiException ae) {
                    System.err.println("[DEBUG_LOG] API Response: " + ae.getResponseBody());
                }
                throw e;
            }
        }
    }
}
