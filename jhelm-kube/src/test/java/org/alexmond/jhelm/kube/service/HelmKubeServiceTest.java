package org.alexmond.jhelm.kube.service;

import tools.jackson.databind.json.JsonMapper;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1DaemonSetStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1DeploymentStatus;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ReplicaSetSpec;
import io.kubernetes.client.openapi.models.V1ReplicaSetStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.PatchOptions;
import org.alexmond.jhelm.core.exception.KubernetesOperationException;
import org.alexmond.jhelm.core.exception.ReleaseStorageException;
import org.alexmond.jhelm.core.exception.WaitTimeoutException;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockConstruction;

class HelmKubeServiceTest {

	@Mock
	private ApiClient apiClient;

	private HelmKubeService kubeService;

	private final JsonMapper objectMapper = JsonMapper.builder().build();

	private MockedConstruction<AppsV1Api> appsV1ApiConstruction;

	private MockedConstruction<CoreV1Api> coreV1ApiConstruction;

	private MockedConstruction<DynamicKubernetesApi> dynamicApiConstruction;

	private CoreV1Api mockCoreV1Api;

	private DynamicKubernetesApi mockDynamicApi;

	private List<Object> lastDynamicApiCtorArgs;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		kubeService = new HelmKubeService(new KubeClient(apiClient));
	}

	@AfterEach
	void tearDown() {
		if (appsV1ApiConstruction != null) {
			appsV1ApiConstruction.close();
		}
		if (coreV1ApiConstruction != null) {
			coreV1ApiConstruction.close();
		}
		if (dynamicApiConstruction != null) {
			dynamicApiConstruction.close();
		}
	}

	private Release createTestRelease(String name, String namespace, int version, String status) {
		return Release.builder()
			.name(name)
			.namespace(namespace)
			.version(version)
			.info(Release.ReleaseInfo.builder()
				.status(ReleaseStatus.fromValue(status))
				.firstDeployed(OffsetDateTime.now())
				.lastDeployed(OffsetDateTime.now())
				.description("Test release")
				.build())
			.build();
	}

	private V1Secret createSecretForRelease(Release release) throws Exception {
		byte[] releaseJson = objectMapper.writeValueAsBytes(release);
		byte[] gzipped = gzip(releaseJson);
		String b64 = Base64.getEncoder().encodeToString(gzipped);

		return new V1Secret()
			.metadata(new V1ObjectMeta().name("sh.helm.release.v1." + release.getName() + ".v" + release.getVersion())
				.namespace(release.getNamespace())
				.putLabelsItem("owner", "helm")
				.putLabelsItem("name", release.getName())
				.putLabelsItem("status", release.getInfo().getStatus().getValue())
				.putLabelsItem("version", String.valueOf(release.getVersion())))
			.type("helm.sh/release.v1")
			.putDataItem("release", b64.getBytes(StandardCharsets.UTF_8));
	}

	private byte[] gzip(byte[] data) throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
			gz.write(data);
		}
		return bos.toByteArray();
	}

	private void setupListRequest(CoreV1Api mock, V1SecretList list) throws Exception {
		var listReq = mock(CoreV1Api.APIlistNamespacedSecretRequest.class);
		when(listReq.labelSelector(anyString())).thenReturn(listReq);
		when(listReq.execute()).thenReturn(list);
		when(mock.listNamespacedSecret(anyString())).thenReturn(listReq);
	}

	private void setupSsaMock() {
		dynamicApiConstruction = mockConstruction(DynamicKubernetesApi.class, (mock, ctx) -> {
			mockDynamicApi = mock;
			lastDynamicApiCtorArgs = new ArrayList<>(ctx.arguments());
			@SuppressWarnings("unchecked")
			KubernetesApiResponse<DynamicKubernetesObject> resp = mock(KubernetesApiResponse.class);
			when(resp.throwsApiException()).thenReturn(resp);
			when(resp.isSuccess()).thenReturn(true);
			when(mock.patch(anyString(), anyString(), anyString(), any(V1Patch.class), any(PatchOptions.class)))
				.thenReturn(resp);
			when(mock.patch(anyString(), anyString(), any(V1Patch.class), any(PatchOptions.class))).thenReturn(resp);
			when(mock.delete(anyString(), anyString())).thenReturn(resp);
			when(mock.delete(anyString())).thenReturn(resp);
		});
	}

	// --- storeRelease ---

	@Test
	void testStoreReleaseCreatesSecret() throws Exception {
		Release release = createTestRelease("myapp", "default", 1, "deployed");

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
			mockCoreV1Api = mock;
			var req = mock(CoreV1Api.APIcreateNamespacedSecretRequest.class);
			when(req.execute()).thenReturn(new V1Secret());
			when(mock.createNamespacedSecret(eq("default"), any(V1Secret.class))).thenReturn(req);
		});

		kubeService.storeRelease(release);
		verify(mockCoreV1Api).createNamespacedSecret(eq("default"), any(V1Secret.class));
	}

	@Test
	void testStoreReleaseReplacesOn409Conflict() throws Exception {
		Release release = createTestRelease("myapp", "default", 1, "deployed");

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
			mockCoreV1Api = mock;
			var createReq = mock(CoreV1Api.APIcreateNamespacedSecretRequest.class);
			when(createReq.execute()).thenThrow(new ApiException(409, "Conflict"));
			when(mock.createNamespacedSecret(eq("default"), any(V1Secret.class))).thenReturn(createReq);

			var replaceReq = mock(CoreV1Api.APIreplaceNamespacedSecretRequest.class);
			when(replaceReq.execute()).thenReturn(new V1Secret());
			when(mock.replaceNamespacedSecret(anyString(), eq("default"), any(V1Secret.class))).thenReturn(replaceReq);
		});

		kubeService.storeRelease(release);
		verify(mockCoreV1Api).replaceNamespacedSecret(eq("sh.helm.release.v1.myapp.v1"), eq("default"),
				any(V1Secret.class));
	}

	@Test
	void testStoreReleaseThrowsOnOtherError() {
		Release release = createTestRelease("myapp", "default", 1, "deployed");

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
			var createReq = mock(CoreV1Api.APIcreateNamespacedSecretRequest.class);
			when(createReq.execute()).thenThrow(new ApiException(500, "Internal error"));
			when(mock.createNamespacedSecret(eq("default"), any(V1Secret.class))).thenReturn(createReq);
		});

		assertThrows(ReleaseStorageException.class, () -> kubeService.storeRelease(release));
	}

	// --- getRelease ---

	@Test
	void testGetReleaseReturnsLatestVersion() throws Exception {
		Release r1 = createTestRelease("myapp", "default", 1, "superseded");
		Release r2 = createTestRelease("myapp", "default", 2, "deployed");
		V1SecretList list = new V1SecretList().items(List.of(createSecretForRelease(r1), createSecretForRelease(r2)));

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> setupListRequest(mock, list));

		Optional<Release> result = kubeService.getRelease("myapp", "default");
		assertTrue(result.isPresent());
		assertEquals(2, result.get().getVersion());
	}

	@Test
	void testGetReleaseReturnsEmptyWhenNone() throws Exception {
		V1SecretList list = new V1SecretList().items(List.of());

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> setupListRequest(mock, list));

		assertTrue(kubeService.getRelease("myapp", "default").isEmpty());
	}

	@Test
	void testGetReleaseThrowsOnDecodeError() throws Exception {
		// Secret with invalid data
		V1Secret badSecret = new V1Secret()
			.metadata(new V1ObjectMeta().name("sh.helm.release.v1.myapp.v1")
				.putLabelsItem("version", "1")
				.putLabelsItem("name", "myapp"))
			.putDataItem("release", "not-valid-base64!!!".getBytes(StandardCharsets.UTF_8));

		V1SecretList list = new V1SecretList().items(List.of(badSecret));

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> setupListRequest(mock, list));

		assertThrows(ReleaseStorageException.class, () -> kubeService.getRelease("myapp", "default"));
	}

	// --- listReleases ---

	@Test
	void testListReleasesGroupsByNameAndPicksLatest() throws Exception {
		Release app1v1 = createTestRelease("app1", "default", 1, "superseded");
		Release app1v2 = createTestRelease("app1", "default", 2, "deployed");
		Release app2v1 = createTestRelease("app2", "default", 1, "deployed");

		V1SecretList list = new V1SecretList().items(List.of(createSecretForRelease(app1v1),
				createSecretForRelease(app1v2), createSecretForRelease(app2v1)));

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> setupListRequest(mock, list));

		List<Release> releases = kubeService.listReleases("default");
		assertEquals(2, releases.size());
		Release app1 = releases.stream().filter((r) -> "app1".equals(r.getName())).findFirst().orElse(null);
		assertNotNull(app1);
		assertEquals(2, app1.getVersion());
	}

	@Test
	void testListReleasesHandlesDecodeError() throws Exception {
		// Mix of good and bad Secrets
		Release good = createTestRelease("good-app", "default", 1, "deployed");
		V1Secret badSecret = new V1Secret()
			.metadata(new V1ObjectMeta().name("sh.helm.release.v1.bad-app.v1")
				.putLabelsItem("version", "1")
				.putLabelsItem("name", "bad-app"))
			.putDataItem("release", "invalid-data".getBytes(StandardCharsets.UTF_8));

		V1SecretList list = new V1SecretList().items(List.of(createSecretForRelease(good), badSecret));

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> setupListRequest(mock, list));

		// Should return only the good release, skipping the bad one
		List<Release> releases = kubeService.listReleases("default");
		assertEquals(1, releases.size());
		assertEquals("good-app", releases.get(0).getName());
	}

	@Test
	void testListReleasesReturnsEmptyWhenNone() throws Exception {
		V1SecretList list = new V1SecretList().items(List.of());

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> setupListRequest(mock, list));

		assertTrue(kubeService.listReleases("staging").isEmpty());
	}

	// --- getReleaseHistory ---

	@Test
	void testGetReleaseHistoryReturnsSortedDescending() throws Exception {
		Release r1 = createTestRelease("myapp", "default", 1, "superseded");
		Release r2 = createTestRelease("myapp", "default", 2, "superseded");
		Release r3 = createTestRelease("myapp", "default", 3, "deployed");

		V1SecretList list = new V1SecretList()
			.items(List.of(createSecretForRelease(r1), createSecretForRelease(r3), createSecretForRelease(r2)));

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> setupListRequest(mock, list));

		List<Release> history = kubeService.getReleaseHistory("myapp", "default");
		assertEquals(3, history.size());
		assertEquals(3, history.get(0).getVersion());
		assertEquals(2, history.get(1).getVersion());
		assertEquals(1, history.get(2).getVersion());
	}

	@Test
	void testGetReleaseHistoryThrowsOnDecodeError() throws Exception {
		V1Secret badSecret = new V1Secret()
			.metadata(new V1ObjectMeta().name("sh.helm.release.v1.myapp.v1")
				.putLabelsItem("version", "1")
				.putLabelsItem("name", "myapp"))
			.putDataItem("release", "bad-data".getBytes(StandardCharsets.UTF_8));

		V1SecretList list = new V1SecretList().items(List.of(badSecret));

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> setupListRequest(mock, list));

		assertThrows(ReleaseStorageException.class, () -> kubeService.getReleaseHistory("myapp", "default"));
	}

	// --- deleteReleaseHistory ---

	@Test
	void testDeleteReleaseHistoryDeletesAllVersions() throws Exception {
		Release r1 = createTestRelease("myapp", "default", 1, "superseded");
		Release r2 = createTestRelease("myapp", "default", 2, "deployed");

		V1SecretList list = new V1SecretList().items(List.of(createSecretForRelease(r1), createSecretForRelease(r2)));

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
			mockCoreV1Api = mock;
			setupListRequest(mock, list);

			var deleteReq = mock(CoreV1Api.APIdeleteNamespacedSecretRequest.class);
			when(deleteReq.execute()).thenReturn(null);
			when(mock.deleteNamespacedSecret(anyString(), eq("default"))).thenReturn(deleteReq);
		});

		kubeService.deleteReleaseHistory("myapp", "default");
		verify(mockCoreV1Api).deleteNamespacedSecret("sh.helm.release.v1.myapp.v1", "default");
		verify(mockCoreV1Api).deleteNamespacedSecret("sh.helm.release.v1.myapp.v2", "default");
	}

	// --- pruneReleaseHistory ---

	private V1SecretList revisionSecrets(String name, String namespace, int from, int to) throws Exception {
		List<V1Secret> items = new ArrayList<>();
		for (int v = from; v <= to; v++) {
			items.add(createSecretForRelease(createTestRelease(name, namespace, v, "superseded")));
		}
		return new V1SecretList().items(items);
	}

	@Test
	void testPruneReleaseHistoryDeletesOldestRevisions() throws Exception {
		// 12 revisions (v1..v12), keep newest 10 -> delete the two lowest versions only.
		V1SecretList list = revisionSecrets("myapp", "default", 1, 12);

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
			mockCoreV1Api = mock;
			setupListRequest(mock, list);
			var deleteReq = mock(CoreV1Api.APIdeleteNamespacedSecretRequest.class);
			when(deleteReq.execute()).thenReturn(null);
			when(mock.deleteNamespacedSecret(anyString(), eq("default"))).thenReturn(deleteReq);
		});

		kubeService.pruneReleaseHistory("myapp", "default", 10);

		// The two oldest (lowest version) Secrets are deleted.
		verify(mockCoreV1Api).deleteNamespacedSecret("sh.helm.release.v1.myapp.v1", "default");
		verify(mockCoreV1Api).deleteNamespacedSecret("sh.helm.release.v1.myapp.v2", "default");
		// The rest are kept, including the highest (current) version.
		verify(mockCoreV1Api, never()).deleteNamespacedSecret("sh.helm.release.v1.myapp.v3", "default");
		verify(mockCoreV1Api, never()).deleteNamespacedSecret("sh.helm.release.v1.myapp.v12", "default");
	}

	@Test
	void testPruneReleaseHistoryZeroMaxHistoryDeletesNothing() {
		// maxHistory <= 0 means no limit: the call returns before any API client is
		// built.
		coreV1ApiConstruction = mockConstruction(CoreV1Api.class);

		kubeService.pruneReleaseHistory("myapp", "default", 0);

		assertTrue(coreV1ApiConstruction.constructed().isEmpty());
	}

	@Test
	void testPruneReleaseHistoryAtLimitDeletesNothing() throws Exception {
		// Exactly maxHistory revisions present -> nothing to prune.
		V1SecretList list = revisionSecrets("myapp", "default", 1, 10);

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
			mockCoreV1Api = mock;
			setupListRequest(mock, list);
			var deleteReq = mock(CoreV1Api.APIdeleteNamespacedSecretRequest.class);
			when(deleteReq.execute()).thenReturn(null);
			when(mock.deleteNamespacedSecret(anyString(), eq("default"))).thenReturn(deleteReq);
		});

		kubeService.pruneReleaseHistory("myapp", "default", 10);

		verify(mockCoreV1Api, never()).deleteNamespacedSecret(anyString(), anyString());
	}

	@Test
	void testPruneReleaseHistoryFewerThanLimitDeletesNothing() throws Exception {
		// Fewer than maxHistory revisions present -> nothing to prune.
		V1SecretList list = revisionSecrets("myapp", "default", 1, 3);

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
			mockCoreV1Api = mock;
			setupListRequest(mock, list);
			var deleteReq = mock(CoreV1Api.APIdeleteNamespacedSecretRequest.class);
			when(deleteReq.execute()).thenReturn(null);
			when(mock.deleteNamespacedSecret(anyString(), eq("default"))).thenReturn(deleteReq);
		});

		kubeService.pruneReleaseHistory("myapp", "default", 10);

		verify(mockCoreV1Api, never()).deleteNamespacedSecret(anyString(), anyString());
	}

	// --- listPods ---

	@Test
	void testListPodsReturnsNames() throws Exception {
		V1PodList podList = new V1PodList().items(List.of(new V1Pod().metadata(new V1ObjectMeta().name("pod-1")),
				new V1Pod().metadata(new V1ObjectMeta().name("pod-2"))));

		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
			var listReq = mock(CoreV1Api.APIlistNamespacedPodRequest.class);
			when(listReq.execute()).thenReturn(podList);
			when(mock.listNamespacedPod("default")).thenReturn(listReq);
		});

		assertEquals(List.of("pod-1", "pod-2"), kubeService.listPods("default"));
	}

	// --- apply (Server-Side Apply) ---

	@Test
	void testApplyNamespacedResourceUsesSSA() throws Exception {
		String yaml = """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: my-deploy
				  namespace: default
				spec:
				  replicas: 1
				""";

		setupSsaMock();
		kubeService.apply("default", yaml);

		assertEquals("apps", lastDynamicApiCtorArgs.get(0));
		assertEquals("v1", lastDynamicApiCtorArgs.get(1));
		assertEquals("deployments", lastDynamicApiCtorArgs.get(2));

		ArgumentCaptor<PatchOptions> optionsCaptor = ArgumentCaptor.forClass(PatchOptions.class);
		verify(mockDynamicApi).patch(eq("default"), eq("my-deploy"), eq(V1Patch.PATCH_FORMAT_APPLY_YAML),
				any(V1Patch.class), optionsCaptor.capture());
		assertEquals("helm", optionsCaptor.getValue().getFieldManager());
		assertEquals(Boolean.TRUE, optionsCaptor.getValue().getForce());
	}

	@Test
	void testApplyCoreV1ResourceUsesSSA() throws Exception {
		String yaml = """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: my-config
				  namespace: default
				data:
				  key: value
				""";

		setupSsaMock();
		kubeService.apply("default", yaml);

		assertEquals("", lastDynamicApiCtorArgs.get(0));
		assertEquals("v1", lastDynamicApiCtorArgs.get(1));
		assertEquals("configmaps", lastDynamicApiCtorArgs.get(2));

		verify(mockDynamicApi).patch(eq("default"), eq("my-config"), eq(V1Patch.PATCH_FORMAT_APPLY_YAML),
				any(V1Patch.class), any(PatchOptions.class));
	}

	@Test
	void testApplyClusterScopedResourceUsesSSA() throws Exception {
		String yaml = """
				apiVersion: v1
				kind: Namespace
				metadata:
				  name: my-ns
				""";

		setupSsaMock();
		kubeService.apply("", yaml);

		assertEquals("", lastDynamicApiCtorArgs.get(0));
		assertEquals("v1", lastDynamicApiCtorArgs.get(1));
		assertEquals("namespaces", lastDynamicApiCtorArgs.get(2));

		verify(mockDynamicApi).patch(eq("my-ns"), eq(V1Patch.PATCH_FORMAT_APPLY_YAML), any(V1Patch.class),
				any(PatchOptions.class));
	}

	@Test
	void testApplyThrowsOnApiException() {
		String yaml = """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: my-deploy
				  namespace: default
				""";

		dynamicApiConstruction = mockConstruction(DynamicKubernetesApi.class, (mock, ctx) -> {
			mockDynamicApi = mock;
			@SuppressWarnings("unchecked")
			KubernetesApiResponse<DynamicKubernetesObject> resp = mock(KubernetesApiResponse.class);
			when(resp.throwsApiException()).thenThrow(new ApiException(500, "Server error"));
			when(mock.patch(anyString(), anyString(), anyString(), any(V1Patch.class), any(PatchOptions.class)))
				.thenReturn(resp);
		});

		assertThrows(KubernetesOperationException.class, () -> kubeService.apply("default", yaml));
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

		setupSsaMock();
		kubeService.delete("default", yaml);

		assertEquals("apps", lastDynamicApiCtorArgs.get(0));
		assertEquals("v1", lastDynamicApiCtorArgs.get(1));
		assertEquals("deployments", lastDynamicApiCtorArgs.get(2));
		verify(mockDynamicApi).delete("default", "my-deploy");
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

		dynamicApiConstruction = mockConstruction(DynamicKubernetesApi.class, (mock, ctx) -> {
			mockDynamicApi = mock;
			@SuppressWarnings("unchecked")
			KubernetesApiResponse<DynamicKubernetesObject> resp = mock(KubernetesApiResponse.class);
			when(resp.isSuccess()).thenReturn(false);
			when(resp.getHttpStatusCode()).thenReturn(404);
			when(mock.delete(anyString(), anyString())).thenReturn(resp);
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

		dynamicApiConstruction = mockConstruction(DynamicKubernetesApi.class, (mock, ctx) -> {
			mockDynamicApi = mock;
			@SuppressWarnings("unchecked")
			KubernetesApiResponse<DynamicKubernetesObject> resp = mock(KubernetesApiResponse.class);
			when(resp.isSuccess()).thenReturn(false);
			when(resp.getHttpStatusCode()).thenReturn(500);
			when(resp.throwsApiException()).thenThrow(new ApiException(500, "Server error"));
			when(mock.delete(anyString(), anyString())).thenReturn(resp);
		});

		assertThrows(KubernetesOperationException.class, () -> kubeService.delete("default", yaml));
	}

	@Test
	void testDeleteClusterScopedResource() throws Exception {
		String yaml = """
				apiVersion: v1
				kind: Namespace
				metadata:
				  name: test-ns
				""";

		setupSsaMock();
		kubeService.delete("", yaml);

		assertEquals("", lastDynamicApiCtorArgs.get(0));
		assertEquals("v1", lastDynamicApiCtorArgs.get(1));
		assertEquals("namespaces", lastDynamicApiCtorArgs.get(2));
		verify(mockDynamicApi).delete("test-ns");
	}

	// --- ensureNamespace ---

	@Test
	void testEnsureNamespaceCreatesNamespace() throws Exception {
		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
			mockCoreV1Api = mock;
			var createReq = mock(CoreV1Api.APIcreateNamespaceRequest.class);
			when(createReq.execute()).thenReturn(new V1Namespace());
			when(mock.createNamespace(any(V1Namespace.class))).thenReturn(createReq);
		});

		kubeService.ensureNamespace("my-ns");
		verify(mockCoreV1Api).createNamespace(any(V1Namespace.class));
	}

	@Test
	void testEnsureNamespaceSwallows409Conflict() {
		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
			var createReq = mock(CoreV1Api.APIcreateNamespaceRequest.class);
			when(createReq.execute()).thenThrow(new ApiException(409, "Conflict"));
			when(mock.createNamespace(any(V1Namespace.class))).thenReturn(createReq);
		});

		// An already-existing namespace (409) is a no-op, not an error.
		assertDoesNotThrow(() -> kubeService.ensureNamespace("default"));
	}

	@Test
	void testEnsureNamespaceThrowsOnOtherApiError() {
		coreV1ApiConstruction = mockConstruction(CoreV1Api.class, (mock, ctx) -> {
			var createReq = mock(CoreV1Api.APIcreateNamespaceRequest.class);
			when(createReq.execute()).thenThrow(new ApiException(500, "Server error"));
			when(mock.createNamespace(any(V1Namespace.class))).thenReturn(createReq);
		});

		assertThrows(KubernetesOperationException.class, () -> kubeService.ensureNamespace("my-ns"));
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
			when(mock.replaceNamespacedConfigMap(eq("my-config"), eq("default"), any(V1ConfigMap.class)))
				.thenReturn(replaceReq);
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

		assertThrows(KubernetesOperationException.class, () -> kubeService.installConfigMap("default", yaml));
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

	// --- getResourceStatuses ---

	@Test
	void testGetResourceStatusesServiceReturnsReady() throws Exception {
		String manifest = """
				apiVersion: v1
				kind: Service
				metadata:
				  name: my-svc
				  namespace: default
				""";

		List<ResourceStatus> statuses = kubeService.getResourceStatuses("default", manifest);

		assertEquals(1, statuses.size());
		assertEquals("Service", statuses.get(0).getKind());
		assertEquals("my-svc", statuses.get(0).getName());
		assertTrue(statuses.get(0).isReady());
	}

	@Test
	void testGetResourceStatusesDeploymentReady() throws Exception {
		String manifest = """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: my-deploy
				  namespace: default
				""";

		appsV1ApiConstruction = mockConstruction(AppsV1Api.class, (mock, ctx) -> {
			V1Deployment dep = new V1Deployment().metadata(new V1ObjectMeta().generation(2L))
				.spec(new V1DeploymentSpec().replicas(2))
				.status(new V1DeploymentStatus().observedGeneration(2L)
					.readyReplicas(2)
					.updatedReplicas(2)
					.availableReplicas(2));
			var readReq = mock(AppsV1Api.APIreadNamespacedDeploymentRequest.class);
			when(readReq.execute()).thenReturn(dep);
			when(mock.readNamespacedDeployment(eq("my-deploy"), eq("default"))).thenReturn(readReq);
		});

		List<ResourceStatus> statuses = kubeService.getResourceStatuses("default", manifest);

		assertEquals(1, statuses.size());
		assertEquals("Deployment", statuses.get(0).getKind());
		assertTrue(statuses.get(0).isReady());
	}

	@Test
	void testGetResourceStatusesDeploymentNotReadyWhenObservedGenerationStale() throws Exception {
		// Replicas all look ready, but the controller has not yet observed the latest
		// spec (observedGeneration < generation): this guards a mid-rollout false-ready.
		String manifest = """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: my-deploy
				  namespace: default
				""";

		appsV1ApiConstruction = mockConstruction(AppsV1Api.class, (mock, ctx) -> {
			V1Deployment dep = new V1Deployment().metadata(new V1ObjectMeta().generation(3L))
				.spec(new V1DeploymentSpec().replicas(2))
				.status(new V1DeploymentStatus().observedGeneration(2L)
					.readyReplicas(2)
					.updatedReplicas(2)
					.availableReplicas(2));
			var readReq = mock(AppsV1Api.APIreadNamespacedDeploymentRequest.class);
			when(readReq.execute()).thenReturn(dep);
			when(mock.readNamespacedDeployment(eq("my-deploy"), eq("default"))).thenReturn(readReq);
		});

		List<ResourceStatus> statuses = kubeService.getResourceStatuses("default", manifest);

		assertEquals(1, statuses.size());
		assertFalse(statuses.get(0).isReady());
		assertEquals("waiting for rollout", statuses.get(0).getMessage());
	}

	@Test
	void testGetResourceStatusesDeploymentNotReadyWhenUnavailable() throws Exception {
		// observedGeneration is current and ready/updated meet desired, but available
		// replicas lag: Helm treats this as not ready.
		String manifest = """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: my-deploy
				  namespace: default
				""";

		appsV1ApiConstruction = mockConstruction(AppsV1Api.class, (mock, ctx) -> {
			V1Deployment dep = new V1Deployment().metadata(new V1ObjectMeta().generation(1L))
				.spec(new V1DeploymentSpec().replicas(2))
				.status(new V1DeploymentStatus().observedGeneration(1L)
					.readyReplicas(2)
					.updatedReplicas(2)
					.availableReplicas(1));
			var readReq = mock(AppsV1Api.APIreadNamespacedDeploymentRequest.class);
			when(readReq.execute()).thenReturn(dep);
			when(mock.readNamespacedDeployment(eq("my-deploy"), eq("default"))).thenReturn(readReq);
		});

		List<ResourceStatus> statuses = kubeService.getResourceStatuses("default", manifest);

		assertEquals(1, statuses.size());
		assertFalse(statuses.get(0).isReady());
		assertEquals("2/2 replicas ready", statuses.get(0).getMessage());
	}

	@Test
	void testGetResourceStatusesReplicaSetReady() throws Exception {
		String manifest = """
				apiVersion: apps/v1
				kind: ReplicaSet
				metadata:
				  name: my-rs
				  namespace: default
				""";

		appsV1ApiConstruction = mockConstruction(AppsV1Api.class, (mock, ctx) -> {
			V1ReplicaSet rs = new V1ReplicaSet().metadata(new V1ObjectMeta().generation(1L))
				.spec(new V1ReplicaSetSpec().replicas(3))
				.status(new V1ReplicaSetStatus().observedGeneration(1L).readyReplicas(3));
			var readReq = mock(AppsV1Api.APIreadNamespacedReplicaSetRequest.class);
			when(readReq.execute()).thenReturn(rs);
			when(mock.readNamespacedReplicaSet(eq("my-rs"), eq("default"))).thenReturn(readReq);
		});

		List<ResourceStatus> statuses = kubeService.getResourceStatuses("default", manifest);

		assertEquals(1, statuses.size());
		assertEquals("ReplicaSet", statuses.get(0).getKind());
		assertTrue(statuses.get(0).isReady());
	}

	@Test
	void testGetResourceStatusesReplicaSetNotReady() throws Exception {
		String manifest = """
				apiVersion: apps/v1
				kind: ReplicaSet
				metadata:
				  name: my-rs
				  namespace: default
				""";

		appsV1ApiConstruction = mockConstruction(AppsV1Api.class, (mock, ctx) -> {
			V1ReplicaSet rs = new V1ReplicaSet().metadata(new V1ObjectMeta().generation(1L))
				.spec(new V1ReplicaSetSpec().replicas(3))
				.status(new V1ReplicaSetStatus().observedGeneration(1L).readyReplicas(1));
			var readReq = mock(AppsV1Api.APIreadNamespacedReplicaSetRequest.class);
			when(readReq.execute()).thenReturn(rs);
			when(mock.readNamespacedReplicaSet(eq("my-rs"), eq("default"))).thenReturn(readReq);
		});

		List<ResourceStatus> statuses = kubeService.getResourceStatuses("default", manifest);

		assertEquals(1, statuses.size());
		assertEquals("ReplicaSet", statuses.get(0).getKind());
		assertFalse(statuses.get(0).isReady());
		assertEquals("1/3 replicas ready", statuses.get(0).getMessage());
	}

	@Test
	void testGetResourceStatusesDaemonSetReady() throws Exception {
		String manifest = """
				apiVersion: apps/v1
				kind: DaemonSet
				metadata:
				  name: my-ds
				  namespace: default
				""";

		appsV1ApiConstruction = mockConstruction(AppsV1Api.class, (mock, ctx) -> {
			V1DaemonSet ds = new V1DaemonSet().metadata(new V1ObjectMeta().generation(1L))
				.status(new V1DaemonSetStatus().observedGeneration(1L)
					.desiredNumberScheduled(3)
					.updatedNumberScheduled(3)
					.numberReady(3));
			var readReq = mock(AppsV1Api.APIreadNamespacedDaemonSetRequest.class);
			when(readReq.execute()).thenReturn(ds);
			when(mock.readNamespacedDaemonSet(eq("my-ds"), eq("default"))).thenReturn(readReq);
		});

		List<ResourceStatus> statuses = kubeService.getResourceStatuses("default", manifest);

		assertEquals(1, statuses.size());
		assertEquals("DaemonSet", statuses.get(0).getKind());
		assertTrue(statuses.get(0).isReady());
	}

	@Test
	void testGetResourceStatusesDaemonSetNotReady() throws Exception {
		String manifest = """
				apiVersion: apps/v1
				kind: DaemonSet
				metadata:
				  name: my-ds
				  namespace: default
				""";

		appsV1ApiConstruction = mockConstruction(AppsV1Api.class, (mock, ctx) -> {
			V1DaemonSet ds = new V1DaemonSet().metadata(new V1ObjectMeta().generation(1L))
				.status(new V1DaemonSetStatus().observedGeneration(1L)
					.desiredNumberScheduled(3)
					.updatedNumberScheduled(2)
					.numberReady(1));
			var readReq = mock(AppsV1Api.APIreadNamespacedDaemonSetRequest.class);
			when(readReq.execute()).thenReturn(ds);
			when(mock.readNamespacedDaemonSet(eq("my-ds"), eq("default"))).thenReturn(readReq);
		});

		List<ResourceStatus> statuses = kubeService.getResourceStatuses("default", manifest);

		assertEquals(1, statuses.size());
		assertEquals("DaemonSet", statuses.get(0).getKind());
		assertFalse(statuses.get(0).isReady());
		assertEquals("1/3 ready", statuses.get(0).getMessage());
	}

	@Test
	void testGetResourceStatusesDeploymentNotReady() throws Exception {
		String manifest = """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: my-deploy
				  namespace: default
				""";

		appsV1ApiConstruction = mockConstruction(AppsV1Api.class, (mock, ctx) -> {
			V1Deployment dep = new V1Deployment().spec(new V1DeploymentSpec().replicas(3))
				.status(new V1DeploymentStatus().readyReplicas(1).updatedReplicas(2));
			var readReq = mock(AppsV1Api.APIreadNamespacedDeploymentRequest.class);
			when(readReq.execute()).thenReturn(dep);
			when(mock.readNamespacedDeployment(eq("my-deploy"), eq("default"))).thenReturn(readReq);
		});

		List<ResourceStatus> statuses = kubeService.getResourceStatuses("default", manifest);

		assertEquals(1, statuses.size());
		assertFalse(statuses.get(0).isReady());
		assertEquals("1/3 replicas ready", statuses.get(0).getMessage());
	}

	@Test
	void testGetResourceStatusesApiExceptionReturnsFalse() throws Exception {
		String manifest = """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: bad-deploy
				  namespace: default
				""";

		appsV1ApiConstruction = mockConstruction(AppsV1Api.class, (mock, ctx) -> {
			var readReq = mock(AppsV1Api.APIreadNamespacedDeploymentRequest.class);
			when(readReq.execute()).thenThrow(new ApiException(404, "Not Found"));
			when(mock.readNamespacedDeployment(eq("bad-deploy"), eq("default"))).thenReturn(readReq);
		});

		List<ResourceStatus> statuses = kubeService.getResourceStatuses("default", manifest);

		assertEquals(1, statuses.size());
		assertFalse(statuses.get(0).isReady());
	}

	@Test
	void testGetResourceStatusesEmptyManifestReturnsEmpty() throws Exception {
		List<ResourceStatus> statuses = kubeService.getResourceStatuses("default", "");
		assertTrue(statuses.isEmpty());
	}

	// --- waitForReady ---

	@Test
	void testWaitForReadyWithImmediatelyReadyResources() {
		String manifest = """
				apiVersion: v1
				kind: Service
				metadata:
				  name: my-svc
				  namespace: default
				""";

		assertDoesNotThrow(() -> kubeService.waitForReady("default", manifest, 60));
	}

	@Test
	void testWaitForReadyTimeoutThrowsWaitTimeoutException() {
		String manifest = """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: my-deploy
				  namespace: default
				""";

		appsV1ApiConstruction = mockConstruction(AppsV1Api.class, (mock, ctx) -> {
			V1Deployment dep = new V1Deployment().spec(new V1DeploymentSpec().replicas(3))
				.status(new V1DeploymentStatus().readyReplicas(0).updatedReplicas(0));
			var readReq = mock(AppsV1Api.APIreadNamespacedDeploymentRequest.class);
			when(readReq.execute()).thenReturn(dep);
			when(mock.readNamespacedDeployment(anyString(), anyString())).thenReturn(readReq);
		});

		WaitTimeoutException ex = assertThrows(WaitTimeoutException.class,
				() -> kubeService.waitForReady("default", manifest, 0));
		assertFalse(ex.getPendingResources().isEmpty());
		assertEquals("Deployment", ex.getPendingResources().get(0).getKind());
	}

	// --- inferPlural ---

	@ParameterizedTest
	@CsvSource({ "ConfigMap, configmaps", "Service, services", "Pod, pods", "Secret, secrets",
			"NetworkPolicy, networkpolicies", "IngressPolicy, ingresspolicies" })
	void testInferPlural(String kind, String expected) throws Exception {
		var method = HelmKubeService.class.getDeclaredMethod("inferPlural", String.class);
		method.setAccessible(true);
		assertEquals(expected, method.invoke(kubeService, kind));
	}

	// --- Secret naming convention ---

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 5, 10, 100 })
	void testSecretNamingConvention(int version) throws Exception {
		Release release = createTestRelease("app", "default", version, "deployed");
		V1Secret secret = createSecretForRelease(release);

		assertEquals("sh.helm.release.v1.app.v" + version, secret.getMetadata().getName());
		assertEquals(String.valueOf(version), secret.getMetadata().getLabels().get("version"));
		assertEquals("helm", secret.getMetadata().getLabels().get("owner"));
		assertEquals("helm.sh/release.v1", secret.getType());
	}

}
