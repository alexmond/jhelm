package org.alexmond.jhelm.kube;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.*;
import org.alexmond.jhelm.core.Release;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HelmKubeServiceTest {

    @Mock
    private ApiClient apiClient;

    private HelmKubeService kubeService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private MockedConstruction<CoreV1Api> coreV1ApiConstruction;
    private MockedConstruction<CustomObjectsApi> customObjectsApiConstruction;
    private CoreV1Api mockCoreV1Api;
    private CustomObjectsApi mockCustomObjectsApi;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        kubeService = new HelmKubeService(apiClient);
    }

    @AfterEach
    void tearDown() {
        if (coreV1ApiConstruction != null) coreV1ApiConstruction.close();
        if (customObjectsApiConstruction != null) customObjectsApiConstruction.close();
    }

    private Release createTestRelease(String name, String namespace, int version, String status) {
        return Release.builder()
                .name(name)
                .namespace(namespace)
                .version(version)
                .info(Release.ReleaseInfo.builder()
                        .status(status)
                        .firstDeployed(OffsetDateTime.now())
                        .lastDeployed(OffsetDateTime.now())
                        .description("Test release")
                        .build())
                .build();
    }

    private V1ConfigMap createConfigMapForRelease(Release release) throws Exception {
        byte[] releaseJson = objectMapper.writeValueAsBytes(release);
        String encoded = Base64.getEncoder().encodeToString(releaseJson);

        return new V1ConfigMap()
                .metadata(new V1ObjectMeta()
                        .name("sh.helm.release.v1." + release.getName() + ".v" + release.getVersion())
                        .namespace(release.getNamespace())
                        .putLabelsItem("owner", "helm")
                        .putLabelsItem("name", release.getName())
                        .putLabelsItem("status", release.getInfo().getStatus())
                        .putLabelsItem("version", String.valueOf(release.getVersion())))
                .putDataItem("release", encoded);
    }

    private void setupListRequest(CoreV1Api mock, V1ConfigMapList list) throws Exception {
        var listReq = mock(CoreV1Api.APIlistNamespacedConfigMapRequest.class);
        when(listReq.labelSelector(anyString())).thenReturn(listReq);
        when(listReq.execute()).thenReturn(list);
        when(mock.listNamespacedConfigMap(anyString())).thenReturn(listReq);
    }

    // --- storeRelease ---

    @Test
    void testStoreReleaseCreatesConfigMap() throws Exception {
        Release release = createTestRelease("myapp", "default", 1, "deployed");

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            mockCoreV1Api = mock;
            var req = mock(CoreV1Api.APIcreateNamespacedConfigMapRequest.class);
            when(req.execute()).thenReturn(new V1ConfigMap());
            when(mock.createNamespacedConfigMap(eq("default"), any(V1ConfigMap.class))).thenReturn(req);
        });

        kubeService.storeRelease(release);
        verify(mockCoreV1Api).createNamespacedConfigMap(eq("default"), any(V1ConfigMap.class));
    }

    @Test
    void testStoreReleaseReplacesOn409Conflict() throws Exception {
        Release release = createTestRelease("myapp", "default", 1, "deployed");

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            mockCoreV1Api = mock;
            var createReq = mock(CoreV1Api.APIcreateNamespacedConfigMapRequest.class);
            when(createReq.execute()).thenThrow(new ApiException(409, "Conflict"));
            when(mock.createNamespacedConfigMap(eq("default"), any(V1ConfigMap.class))).thenReturn(createReq);

            var replaceReq = mock(CoreV1Api.APIreplaceNamespacedConfigMapRequest.class);
            when(replaceReq.execute()).thenReturn(new V1ConfigMap());
            when(mock.replaceNamespacedConfigMap(anyString(), eq("default"), any(V1ConfigMap.class))).thenReturn(replaceReq);
        });

        kubeService.storeRelease(release);
        verify(mockCoreV1Api).replaceNamespacedConfigMap(
                eq("sh.helm.release.v1.myapp.v1"), eq("default"), any(V1ConfigMap.class));
    }

    @Test
    void testStoreReleaseThrowsOnOtherError() {
        Release release = createTestRelease("myapp", "default", 1, "deployed");

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            var createReq = mock(CoreV1Api.APIcreateNamespacedConfigMapRequest.class);
            when(createReq.execute()).thenThrow(new ApiException(500, "Internal error"));
            when(mock.createNamespacedConfigMap(eq("default"), any(V1ConfigMap.class))).thenReturn(createReq);
        });

        assertThrows(RuntimeException.class, () -> kubeService.storeRelease(release));
    }

    // --- getRelease ---

    @Test
    void testGetReleaseReturnsLatestVersion() throws Exception {
        Release r1 = createTestRelease("myapp", "default", 1, "superseded");
        Release r2 = createTestRelease("myapp", "default", 2, "deployed");
        V1ConfigMapList list = new V1ConfigMapList()
                .items(List.of(createConfigMapForRelease(r1), createConfigMapForRelease(r2)));

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            setupListRequest(mock, list);
        });

        Optional<Release> result = kubeService.getRelease("myapp", "default");
        assertTrue(result.isPresent());
        assertEquals(2, result.get().getVersion());
    }

    @Test
    void testGetReleaseReturnsEmptyWhenNone() throws Exception {
        V1ConfigMapList list = new V1ConfigMapList().items(List.of());

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            setupListRequest(mock, list);
        });

        assertTrue(kubeService.getRelease("myapp", "default").isEmpty());
    }

    @Test
    void testGetReleaseThrowsOnDecodeError() throws Exception {
        // ConfigMap with invalid base64 data
        V1ConfigMap badCm = new V1ConfigMap()
                .metadata(new V1ObjectMeta()
                        .putLabelsItem("version", "1")
                        .putLabelsItem("name", "myapp"))
                .putDataItem("release", "not-valid-base64!!!");

        V1ConfigMapList list = new V1ConfigMapList().items(List.of(badCm));

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            setupListRequest(mock, list);
        });

        assertThrows(RuntimeException.class, () -> kubeService.getRelease("myapp", "default"));
    }

    // --- listReleases ---

    @Test
    void testListReleasesGroupsByNameAndPicksLatest() throws Exception {
        Release app1v1 = createTestRelease("app1", "default", 1, "superseded");
        Release app1v2 = createTestRelease("app1", "default", 2, "deployed");
        Release app2v1 = createTestRelease("app2", "default", 1, "deployed");

        V1ConfigMapList list = new V1ConfigMapList()
                .items(List.of(
                        createConfigMapForRelease(app1v1),
                        createConfigMapForRelease(app1v2),
                        createConfigMapForRelease(app2v1)));

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            setupListRequest(mock, list);
        });

        List<Release> releases = kubeService.listReleases("default");
        assertEquals(2, releases.size());
        Release app1 = releases.stream().filter(r -> "app1".equals(r.getName())).findFirst().orElse(null);
        assertNotNull(app1);
        assertEquals(2, app1.getVersion());
    }

    @Test
    void testListReleasesHandlesDecodeError() throws Exception {
        // Mix of good and bad ConfigMaps
        Release good = createTestRelease("good-app", "default", 1, "deployed");
        V1ConfigMap badCm = new V1ConfigMap()
                .metadata(new V1ObjectMeta()
                        .putLabelsItem("version", "1")
                        .putLabelsItem("name", "bad-app"))
                .putDataItem("release", "invalid-base64");

        V1ConfigMapList list = new V1ConfigMapList()
                .items(List.of(createConfigMapForRelease(good), badCm));

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            setupListRequest(mock, list);
        });

        // Should return only the good release, skipping the bad one
        List<Release> releases = kubeService.listReleases("default");
        assertEquals(1, releases.size());
        assertEquals("good-app", releases.get(0).getName());
    }

    @Test
    void testListReleasesReturnsEmptyWhenNone() throws Exception {
        V1ConfigMapList list = new V1ConfigMapList().items(List.of());

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            setupListRequest(mock, list);
        });

        assertTrue(kubeService.listReleases("staging").isEmpty());
    }

    // --- getReleaseHistory ---

    @Test
    void testGetReleaseHistoryReturnsSortedDescending() throws Exception {
        Release r1 = createTestRelease("myapp", "default", 1, "superseded");
        Release r2 = createTestRelease("myapp", "default", 2, "superseded");
        Release r3 = createTestRelease("myapp", "default", 3, "deployed");

        V1ConfigMapList list = new V1ConfigMapList()
                .items(List.of(
                        createConfigMapForRelease(r1),
                        createConfigMapForRelease(r3),
                        createConfigMapForRelease(r2)));

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            setupListRequest(mock, list);
        });

        List<Release> history = kubeService.getReleaseHistory("myapp", "default");
        assertEquals(3, history.size());
        assertEquals(3, history.get(0).getVersion());
        assertEquals(2, history.get(1).getVersion());
        assertEquals(1, history.get(2).getVersion());
    }

    @Test
    void testGetReleaseHistoryThrowsOnDecodeError() throws Exception {
        V1ConfigMap badCm = new V1ConfigMap()
                .metadata(new V1ObjectMeta()
                        .putLabelsItem("version", "1")
                        .putLabelsItem("name", "myapp"))
                .putDataItem("release", "bad-data");

        V1ConfigMapList list = new V1ConfigMapList().items(List.of(badCm));

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            setupListRequest(mock, list);
        });

        assertThrows(RuntimeException.class, () -> kubeService.getReleaseHistory("myapp", "default"));
    }

    // --- deleteReleaseHistory ---

    @Test
    void testDeleteReleaseHistoryDeletesAllVersions() throws Exception {
        Release r1 = createTestRelease("myapp", "default", 1, "superseded");
        Release r2 = createTestRelease("myapp", "default", 2, "deployed");

        V1ConfigMapList list = new V1ConfigMapList()
                .items(List.of(createConfigMapForRelease(r1), createConfigMapForRelease(r2)));

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            mockCoreV1Api = mock;
            setupListRequest(mock, list);

            var deleteReq = mock(CoreV1Api.APIdeleteNamespacedConfigMapRequest.class);
            when(deleteReq.execute()).thenReturn(null);
            when(mock.deleteNamespacedConfigMap(anyString(), eq("default"))).thenReturn(deleteReq);
        });

        kubeService.deleteReleaseHistory("myapp", "default");
        verify(mockCoreV1Api).deleteNamespacedConfigMap("sh.helm.release.v1.myapp.v1", "default");
        verify(mockCoreV1Api).deleteNamespacedConfigMap("sh.helm.release.v1.myapp.v2", "default");
    }

    // --- listPods ---

    @Test
    void testListPodsReturnsNames() throws Exception {
        V1PodList podList = new V1PodList()
                .items(List.of(
                        new V1Pod().metadata(new V1ObjectMeta().name("pod-1")),
                        new V1Pod().metadata(new V1ObjectMeta().name("pod-2"))));

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            var listReq = mock(CoreV1Api.APIlistNamespacedPodRequest.class);
            when(listReq.execute()).thenReturn(podList);
            when(mock.listNamespacedPod("default")).thenReturn(listReq);
        });

        assertEquals(List.of("pod-1", "pod-2"), kubeService.listPods("default"));
    }

    // --- apply ---

    @Test
    void testApplyConfigMapCreatesNew() throws Exception {
        String yaml = """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: my-config
                  namespace: default
                data:
                  key: value
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            mockCoreV1Api = mock;
            // readNamespacedConfigMap throws 404 -> create
            var readReq = mock(CoreV1Api.APIreadNamespacedConfigMapRequest.class);
            when(readReq.execute()).thenThrow(new ApiException(404, "Not found"));
            when(mock.readNamespacedConfigMap(eq("my-config"), eq("default"))).thenReturn(readReq);

            var createReq = mock(CoreV1Api.APIcreateNamespacedConfigMapRequest.class);
            when(createReq.execute()).thenReturn(new V1ConfigMap());
            when(mock.createNamespacedConfigMap(eq("default"), any(V1ConfigMap.class))).thenReturn(createReq);
        });

        kubeService.apply("default", yaml);
        verify(mockCoreV1Api).createNamespacedConfigMap(eq("default"), any(V1ConfigMap.class));
    }

    @Test
    void testApplyConfigMapReplacesExisting() throws Exception {
        String yaml = """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: my-config
                  namespace: default
                data:
                  key: value
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            mockCoreV1Api = mock;
            var readReq = mock(CoreV1Api.APIreadNamespacedConfigMapRequest.class);
            when(readReq.execute()).thenReturn(new V1ConfigMap());
            when(mock.readNamespacedConfigMap(eq("my-config"), eq("default"))).thenReturn(readReq);

            var replaceReq = mock(CoreV1Api.APIreplaceNamespacedConfigMapRequest.class);
            when(replaceReq.execute()).thenReturn(new V1ConfigMap());
            when(mock.replaceNamespacedConfigMap(eq("my-config"), eq("default"), any(V1ConfigMap.class))).thenReturn(replaceReq);
        });

        kubeService.apply("default", yaml);
        verify(mockCoreV1Api).replaceNamespacedConfigMap(eq("my-config"), eq("default"), any(V1ConfigMap.class));
    }

    @Test
    void testApplyServiceCreatesNew() throws Exception {
        String yaml = """
                apiVersion: v1
                kind: Service
                metadata:
                  name: my-svc
                  namespace: default
                spec:
                  ports:
                  - port: 80
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            mockCoreV1Api = mock;
            var readReq = mock(CoreV1Api.APIreadNamespacedServiceRequest.class);
            when(readReq.execute()).thenThrow(new ApiException(404, "Not found"));
            when(mock.readNamespacedService(eq("my-svc"), eq("default"))).thenReturn(readReq);

            var createReq = mock(CoreV1Api.APIcreateNamespacedServiceRequest.class);
            when(createReq.execute()).thenReturn(new V1Service());
            when(mock.createNamespacedService(eq("default"), any(V1Service.class))).thenReturn(createReq);
        });

        kubeService.apply("default", yaml);
        verify(mockCoreV1Api).createNamespacedService(eq("default"), any(V1Service.class));
    }

    @Test
    void testApplySecretCreatesNew() throws Exception {
        String yaml = """
                apiVersion: v1
                kind: Secret
                metadata:
                  name: my-secret
                  namespace: default
                type: Opaque
                data:
                  password: cGFzc3dvcmQ=
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            mockCoreV1Api = mock;
            var readReq = mock(CoreV1Api.APIreadNamespacedSecretRequest.class);
            when(readReq.execute()).thenThrow(new ApiException(404, "Not found"));
            when(mock.readNamespacedSecret(eq("my-secret"), eq("default"))).thenReturn(readReq);

            var createReq = mock(CoreV1Api.APIcreateNamespacedSecretRequest.class);
            when(createReq.execute()).thenReturn(new V1Secret());
            when(mock.createNamespacedSecret(eq("default"), any(V1Secret.class))).thenReturn(createReq);
        });

        kubeService.apply("default", yaml);
        verify(mockCoreV1Api).createNamespacedSecret(eq("default"), any(V1Secret.class));
    }

    @Test
    void testApplyServiceReplacesExisting() throws Exception {
        String yaml = """
                apiVersion: v1
                kind: Service
                metadata:
                  name: my-svc
                  namespace: default
                spec:
                  ports:
                  - port: 80
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            mockCoreV1Api = mock;
            var readReq = mock(CoreV1Api.APIreadNamespacedServiceRequest.class);
            when(readReq.execute()).thenReturn(new V1Service());
            when(mock.readNamespacedService(eq("my-svc"), eq("default"))).thenReturn(readReq);

            var replaceReq = mock(CoreV1Api.APIreplaceNamespacedServiceRequest.class);
            when(replaceReq.execute()).thenReturn(new V1Service());
            when(mock.replaceNamespacedService(eq("my-svc"), eq("default"), any(V1Service.class))).thenReturn(replaceReq);
        });

        kubeService.apply("default", yaml);
        verify(mockCoreV1Api).replaceNamespacedService(eq("my-svc"), eq("default"), any(V1Service.class));
    }

    @Test
    void testApplySecretReplacesExisting() throws Exception {
        String yaml = """
                apiVersion: v1
                kind: Secret
                metadata:
                  name: my-secret
                  namespace: default
                type: Opaque
                data:
                  password: cGFzc3dvcmQ=
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            mockCoreV1Api = mock;
            var readReq = mock(CoreV1Api.APIreadNamespacedSecretRequest.class);
            when(readReq.execute()).thenReturn(new V1Secret());
            when(mock.readNamespacedSecret(eq("my-secret"), eq("default"))).thenReturn(readReq);

            var replaceReq = mock(CoreV1Api.APIreplaceNamespacedSecretRequest.class);
            when(replaceReq.execute()).thenReturn(new V1Secret());
            when(mock.replaceNamespacedSecret(eq("my-secret"), eq("default"), any(V1Secret.class))).thenReturn(replaceReq);
        });

        kubeService.apply("default", yaml);
        verify(mockCoreV1Api).replaceNamespacedSecret(eq("my-secret"), eq("default"), any(V1Secret.class));
    }

    @Test
    void testApplyServiceThrowsOnNon404Error() {
        String yaml = """
                apiVersion: v1
                kind: Service
                metadata:
                  name: my-svc
                  namespace: default
                spec:
                  ports:
                  - port: 80
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            var readReq = mock(CoreV1Api.APIreadNamespacedServiceRequest.class);
            when(readReq.execute()).thenThrow(new ApiException(500, "Server error"));
            when(mock.readNamespacedService(eq("my-svc"), eq("default"))).thenReturn(readReq);
        });

        assertThrows(Exception.class, () -> kubeService.apply("default", yaml));
    }

    @Test
    void testApplySecretThrowsOnNon404Error() {
        String yaml = """
                apiVersion: v1
                kind: Secret
                metadata:
                  name: my-secret
                  namespace: default
                type: Opaque
                data:
                  password: cGFzc3dvcmQ=
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            var readReq = mock(CoreV1Api.APIreadNamespacedSecretRequest.class);
            when(readReq.execute()).thenThrow(new ApiException(500, "Server error"));
            when(mock.readNamespacedSecret(eq("my-secret"), eq("default"))).thenReturn(readReq);
        });

        assertThrows(Exception.class, () -> kubeService.apply("default", yaml));
    }

    @Test
    void testApplyConfigMapThrowsOnNon404Error() {
        String yaml = """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: my-config
                  namespace: default
                data:
                  key: value
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            var readReq = mock(CoreV1Api.APIreadNamespacedConfigMapRequest.class);
            when(readReq.execute()).thenThrow(new ApiException(500, "Server error"));
            when(mock.readNamespacedConfigMap(eq("my-config"), eq("default"))).thenReturn(readReq);
        });

        assertThrows(Exception.class, () -> kubeService.apply("default", yaml));
    }

    @Test
    void testApplyUnknownCoreResourceFallsBackToCustomObjects() throws Exception {
        String yaml = """
                apiVersion: v1
                kind: Endpoints
                metadata:
                  name: my-endpoints
                  namespace: default
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class);
        customObjectsApiConstruction = mockConstruction(CustomObjectsApi.class, (mock, ctx) -> {
            mockCustomObjectsApi = mock;
            var getReq = mock(CustomObjectsApi.APIgetNamespacedCustomObjectRequest.class);
            when(getReq.execute()).thenThrow(new ApiException(404, "Not found"));
            when(mock.getNamespacedCustomObject(eq(""), eq("v1"), eq("default"), eq("endpointss"), eq("my-endpoints"))).thenReturn(getReq);

            var createReq = mock(CustomObjectsApi.APIcreateNamespacedCustomObjectRequest.class);
            when(createReq.execute()).thenReturn(new Object());
            when(mock.createNamespacedCustomObject(eq(""), eq("v1"), eq("default"), eq("endpointss"), any())).thenReturn(createReq);
        });

        kubeService.apply("default", yaml);
    }

    // --- delete ---

    @Test
    void testDeleteNamespacedResource() throws Exception {
        String yaml = """
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: my-deploy
                  namespace: default
                """;

        customObjectsApiConstruction = mockConstruction(CustomObjectsApi.class, (mock, ctx) -> {
            mockCustomObjectsApi = mock;
            var deleteReq = mock(CustomObjectsApi.APIdeleteNamespacedCustomObjectRequest.class);
            when(deleteReq.execute()).thenReturn(new Object());
            when(mock.deleteNamespacedCustomObject(eq("apps"), eq("v1"), eq("default"), eq("deployments"), eq("my-deploy"))).thenReturn(deleteReq);
        });

        kubeService.delete("default", yaml);
        verify(mockCustomObjectsApi).deleteNamespacedCustomObject("apps", "v1", "default", "deployments", "my-deploy");
    }

    @Test
    void testDeleteIgnores404() throws Exception {
        String yaml = """
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: my-deploy
                  namespace: default
                """;

        customObjectsApiConstruction = mockConstruction(CustomObjectsApi.class, (mock, ctx) -> {
            var deleteReq = mock(CustomObjectsApi.APIdeleteNamespacedCustomObjectRequest.class);
            when(deleteReq.execute()).thenThrow(new ApiException(404, "Not found"));
            when(mock.deleteNamespacedCustomObject(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(deleteReq);
        });

        // Should not throw on 404
        assertDoesNotThrow(() -> kubeService.delete("default", yaml));
    }

    @Test
    void testDeleteThrowsOnNon404Error() {
        String yaml = """
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: my-deploy
                  namespace: default
                """;

        customObjectsApiConstruction = mockConstruction(CustomObjectsApi.class, (mock, ctx) -> {
            var deleteReq = mock(CustomObjectsApi.APIdeleteNamespacedCustomObjectRequest.class);
            when(deleteReq.execute()).thenThrow(new ApiException(500, "Server error"));
            when(mock.deleteNamespacedCustomObject(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(deleteReq);
        });

        assertThrows(Exception.class, () -> kubeService.delete("default", yaml));
    }

    @Test
    void testDeleteClusterScopedResource() throws Exception {
        String yaml = """
                apiVersion: v1
                kind: Namespace
                metadata:
                  name: test-ns
                """;

        customObjectsApiConstruction = mockConstruction(CustomObjectsApi.class, (mock, ctx) -> {
            mockCustomObjectsApi = mock;
            var deleteReq = mock(CustomObjectsApi.APIdeleteClusterCustomObjectRequest.class);
            when(deleteReq.execute()).thenReturn(new Object());
            when(mock.deleteClusterCustomObject(eq(""), eq("v1"), eq("namespaces"), eq("test-ns"))).thenReturn(deleteReq);
        });

        kubeService.delete("", yaml);
        verify(mockCustomObjectsApi).deleteClusterCustomObject("", "v1", "namespaces", "test-ns");
    }

    // --- installConfigMap ---

    @Test
    void testInstallConfigMapCreatesNew() throws Exception {
        String yaml = """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: my-config
                data:
                  key: value
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            mockCoreV1Api = mock;
            var createReq = mock(CoreV1Api.APIcreateNamespacedConfigMapRequest.class);
            when(createReq.execute()).thenReturn(new V1ConfigMap());
            when(mock.createNamespacedConfigMap(eq("default"), any(V1ConfigMap.class))).thenReturn(createReq);
        });

        kubeService.installConfigMap("default", yaml);
        verify(mockCoreV1Api).createNamespacedConfigMap(eq("default"), any(V1ConfigMap.class));
    }

    @Test
    void testInstallConfigMapReplacesOn409() throws Exception {
        String yaml = """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: my-config
                data:
                  key: value
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            mockCoreV1Api = mock;
            var createReq = mock(CoreV1Api.APIcreateNamespacedConfigMapRequest.class);
            when(createReq.execute()).thenThrow(new ApiException(409, "Conflict"));
            when(mock.createNamespacedConfigMap(eq("default"), any(V1ConfigMap.class))).thenReturn(createReq);

            var replaceReq = mock(CoreV1Api.APIreplaceNamespacedConfigMapRequest.class);
            when(replaceReq.execute()).thenReturn(new V1ConfigMap());
            when(mock.replaceNamespacedConfigMap(eq("my-config"), eq("default"), any(V1ConfigMap.class))).thenReturn(replaceReq);
        });

        kubeService.installConfigMap("default", yaml);
        verify(mockCoreV1Api).replaceNamespacedConfigMap(eq("my-config"), eq("default"), any(V1ConfigMap.class));
    }

    @Test
    void testInstallConfigMapThrowsOnOtherApiError() {
        String yaml = """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: my-config
                data:
                  key: value
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            var createReq = mock(CoreV1Api.APIcreateNamespacedConfigMapRequest.class);
            when(createReq.execute()).thenThrow(new ApiException(500, "Server error"));
            when(mock.createNamespacedConfigMap(eq("default"), any(V1ConfigMap.class))).thenReturn(createReq);
        });

        assertThrows(ApiException.class, () -> kubeService.installConfigMap("default", yaml));
    }

    @Test
    void testInstallConfigMapThrowsOnNonApiError() {
        String yaml = """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: my-config
                data:
                  key: value
                """;

        coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
            var createReq = mock(CoreV1Api.APIcreateNamespacedConfigMapRequest.class);
            when(createReq.execute()).thenThrow(new RuntimeException("Unexpected"));
            when(mock.createNamespacedConfigMap(eq("default"), any(V1ConfigMap.class))).thenReturn(createReq);
        });

        assertThrows(RuntimeException.class, () -> kubeService.installConfigMap("default", yaml));
    }

    // --- inferPlural ---

    @ParameterizedTest
    @CsvSource({
            "ConfigMap, configmaps",
            "Service, services",
            "Pod, pods",
            "Secret, secrets",
            "NetworkPolicy, networkpolicies",
            "IngressPolicy, ingresspolicies",
    })
    void testInferPlural(String kind, String expected) throws Exception {
        var method = HelmKubeService.class.getDeclaredMethod("inferPlural", String.class);
        method.setAccessible(true);
        assertEquals(expected, method.invoke(kubeService, kind));
    }

    // --- ConfigMap naming convention ---

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 10, 100})
    void testConfigMapNamingConvention(int version) throws Exception {
        Release release = createTestRelease("app", "default", version, "deployed");
        V1ConfigMap cm = createConfigMapForRelease(release);

        assertEquals("sh.helm.release.v1.app.v" + version, cm.getMetadata().getName());
        assertEquals(String.valueOf(version), cm.getMetadata().getLabels().get("version"));
        assertEquals("helm", cm.getMetadata().getLabels().get("owner"));
    }
}
