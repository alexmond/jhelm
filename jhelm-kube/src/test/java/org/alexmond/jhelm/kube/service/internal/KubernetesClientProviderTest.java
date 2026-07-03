package org.alexmond.jhelm.kube.service.internal;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.VersionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.anyString;

class KubernetesClientProviderTest {

	@Mock
	private ApiClient apiClient;

	@Mock
	private CoreV1Api coreV1Api;

	@Mock
	private AppsV1Api appsV1Api;

	@Mock
	private VersionApi versionApi;

	private KubernetesClientProvider provider;

	@BeforeEach
	void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);
		provider = new KubernetesClientProvider(new KubeClient(apiClient));
		setField(provider, "coreV1Api", coreV1Api);
		setField(provider, "appsV1Api", appsV1Api);
		setField(provider, "versionApi", versionApi);
	}

	private void setField(Object target, String fieldName, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	private void setAvailable(Boolean value) throws Exception {
		Field field = KubernetesClientProvider.class.getDeclaredField("available");
		field.setAccessible(true);
		field.set(provider, value);
	}

	// --- isAvailable ---

	@Test
	void testIsAvailableReturnsTrueWhenClusterResponds() throws Exception {
		var versionReq = mock(VersionApi.APIgetCodeRequest.class);
		when(versionReq.execute()).thenReturn(new VersionInfo());
		when(versionApi.getCode()).thenReturn(versionReq);

		assertTrue(provider.isAvailable());
	}

	@Test
	void testIsAvailableReturnsFalseOnException() {
		when(versionApi.getCode()).thenThrow(new RuntimeException("Connection refused"));
		assertFalse(provider.isAvailable());
	}

	@Test
	void testIsAvailableCachesResult() {
		when(versionApi.getCode()).thenThrow(new RuntimeException("Connection refused"));
		provider.isAvailable();
		provider.isAvailable();
		verify(versionApi, times(1)).getCode();
	}

	@Test
	void testIsAvailableReturnsCachedTrue() throws Exception {
		setAvailable(true);
		assertTrue(provider.isAvailable());
		verifyNoInteractions(versionApi);
	}

	// --- getVersion ---

	@Test
	void testGetVersionReturnsStubWhenUnavailable() throws Exception {
		setAvailable(false);
		Map<String, Object> version = provider.getVersion();

		assertEquals("1", version.get("Major"));
		assertEquals("35", version.get("Minor"));
		assertEquals("v1.35.0", version.get("GitVersion"));
		assertEquals("unknown", version.get("GitCommit"));
		assertEquals("linux/amd64", version.get("Platform"));
	}

	@Test
	void testGetVersionReturnsRealVersionWhenAvailable() throws Exception {
		setAvailable(true);
		VersionInfo info = new VersionInfo().major("1")
			.minor("29")
			.gitVersion("v1.29.0")
			.gitCommit("abc123")
			.platform("linux/arm64")
			.buildDate("2026-01-01");

		var versionReq = mock(VersionApi.APIgetCodeRequest.class);
		when(versionReq.execute()).thenReturn(info);
		when(versionApi.getCode()).thenReturn(versionReq);

		Map<String, Object> version = provider.getVersion();
		assertEquals("1", version.get("Major"));
		assertEquals("29", version.get("Minor"));
		assertEquals("v1.29.0", version.get("GitVersion"));
		assertEquals("abc123", version.get("GitCommit"));
		assertEquals("linux/arm64", version.get("Platform"));
		assertEquals("2026-01-01", version.get("BuildDate"));
	}

	@Test
	void testGetVersionReturnsStubOnApiException() throws Exception {
		setAvailable(true);
		var versionReq = mock(VersionApi.APIgetCodeRequest.class);
		when(versionReq.execute()).thenThrow(new ApiException(500, "error"));
		when(versionApi.getCode()).thenReturn(versionReq);

		Map<String, Object> version = provider.getVersion();
		assertEquals("v1.35.0", version.get("GitVersion"));
	}

	// --- lookup when unavailable ---

	@Test
	void testLookupReturnsEmptyWhenUnavailable() throws Exception {
		setAvailable(false);
		Map<String, Object> result = provider.lookup("v1", "ConfigMap", "default", "test");
		assertTrue(result.isEmpty());
	}

	// --- lookup exercising fetchCoreV1Resource branches ---

	@ParameterizedTest
	@ValueSource(strings = { "Pod", "Service", "ConfigMap", "Secret", "PersistentVolumeClaim", "ServiceAccount" })
	void testLookupCoreV1ResourceByName(String kind) throws Exception {
		setAvailable(true);
		// The mock coreV1Api returns null by default for unmocked methods
		// fetchCoreV1Resource will return null -> lookup returns Map.of()
		Map<String, Object> result = provider.lookup("v1", kind, "default", "some-name");
		// Result is empty because the mock returns null for the API call
		assertTrue(result.isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = { "Pod", "Service", "ConfigMap", "Secret", "PersistentVolumeClaim", "ServiceAccount" })
	void testLookupCoreV1ResourceList(String kind) throws Exception {
		setAvailable(true);
		Map<String, Object> result = provider.lookup("v1", kind, "default", "");
		assertTrue(result.isEmpty());
	}

	@Test
	void testLookupCoreV1Namespace() throws Exception {
		setAvailable(true);
		// Namespace is special - uses readNamespace/listNamespace (not namespaced)
		Map<String, Object> result = provider.lookup("v1", "Namespace", "", "default");
		assertTrue(result.isEmpty());
	}

	@Test
	void testLookupCoreV1NamespaceList() throws Exception {
		setAvailable(true);
		Map<String, Object> result = provider.lookup("v1", "Namespace", "", "");
		assertTrue(result.isEmpty());
	}

	// --- lookup exercising fetchAppsV1Resource branches ---

	@ParameterizedTest
	@ValueSource(strings = { "Deployment", "StatefulSet", "DaemonSet", "ReplicaSet" })
	void testLookupAppsV1ResourceByName(String kind) throws Exception {
		setAvailable(true);
		Map<String, Object> result = provider.lookup("apps/v1", kind, "default", "some-name");
		assertTrue(result.isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = { "Deployment", "StatefulSet", "DaemonSet", "ReplicaSet" })
	void testLookupAppsV1ResourceList(String kind) throws Exception {
		setAvailable(true);
		Map<String, Object> result = provider.lookup("apps/v1", kind, "default", "");
		assertTrue(result.isEmpty());
	}

	// --- lookup unsupported resources ---

	@ParameterizedTest
	@CsvSource({ "v1, UnsupportedKind", "apps/v1, UnsupportedKind", "unsupported/v1, Deployment" })
	void testLookupReturnsEmptyForUnsupported(String apiVersion, String kind) throws Exception {
		setAvailable(true);
		Map<String, Object> result = provider.lookup(apiVersion, kind, "default", "test");
		assertTrue(result.isEmpty());
	}

	// --- lookup with null/empty namespace defaults to "default" ---

	@Test
	void testLookupWithNullNamespace() throws Exception {
		setAvailable(true);
		// fetchResource replaces null/empty namespace with "default"
		Map<String, Object> result = provider.lookup("v1", "ConfigMap", null, "test");
		assertTrue(result.isEmpty());
	}

	// --- lookup error handling ---

	@Test
	void testLookupHandlesUnexpectedException() throws Exception {
		setAvailable(true);
		// Make coreV1Api throw a runtime exception
		when(coreV1Api.readNamespacedConfigMap(anyString(), anyString()))
			.thenThrow(new RuntimeException("Unexpected error"));

		Map<String, Object> result = provider.lookup("v1", "ConfigMap", "default", "test");
		assertTrue(result.isEmpty());
	}

	// --- lookup with actual resource returned ---

	@Test
	@SuppressWarnings("unchecked")
	void testLookupWithResourceReturned() throws Exception {
		setAvailable(true);

		// #663: the request builder must be .execute()d so lookup returns the real
		// resource
		// (whole object, incl. .data), not the un-executed builder.
		V1ConfigMap cm = new V1ConfigMap().metadata(new V1ObjectMeta().name("test-cm").namespace("default"))
			.putDataItem("greeting", "hello");
		var readReq = mock(CoreV1Api.APIreadNamespacedConfigMapRequest.class);
		when(readReq.execute()).thenReturn(cm);
		when(coreV1Api.readNamespacedConfigMap("test-cm", "default")).thenReturn(readReq);

		Map<String, Object> result = provider.lookup("v1", "ConfigMap", "default", "test-cm");

		assertNotNull(result);
		Map<String, Object> data = (Map<String, Object>) result.get("data");
		assertNotNull(data, "lookup must return the resource's .data, not the request builder (#663)");
		assertEquals("hello", data.get("greeting"));
		Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
		assertEquals("test-cm", metadata.get("name"));
	}

	// --- convertToMap ---

	@Test
	void testConvertToMapWithNull() throws Exception {
		var method = KubernetesClientProvider.class.getDeclaredMethod("convertToMap", Object.class);
		method.setAccessible(true);

		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) method.invoke(provider, (Object) null);
		assertTrue(result.isEmpty());
	}

	@Test
	void testConvertToMapWithMap() throws Exception {
		var method = KubernetesClientProvider.class.getDeclaredMethod("convertToMap", Object.class);
		method.setAccessible(true);

		Map<String, Object> input = Map.of("key", "value");
		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) method.invoke(provider, input);
		assertEquals("value", result.get("key"));
	}

	@Test
	void testConvertToMapWithKubernetesObject() throws Exception {
		var method = KubernetesClientProvider.class.getDeclaredMethod("convertToMap", Object.class);
		method.setAccessible(true);

		V1ConfigMap cm = new V1ConfigMap().metadata(new V1ObjectMeta().name("test").namespace("default"));

		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) method.invoke(provider, cm);
		assertNotNull(result.get("metadata"));
	}

	@Test
	void testConvertToMapWithListObject() throws Exception {
		var method = KubernetesClientProvider.class.getDeclaredMethod("convertToMap", Object.class);
		method.setAccessible(true);

		V1ConfigMapList list = new V1ConfigMapList()
			.items(List.of(new V1ConfigMap().metadata(new V1ObjectMeta().name("cm1"))));

		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) method.invoke(provider, list);
		assertNotNull(result.get("items"));
	}

	@Test
	void testConvertToMapWithObjectWithoutMetadata() throws Exception {
		var method = KubernetesClientProvider.class.getDeclaredMethod("convertToMap", Object.class);
		method.setAccessible(true);

		// Use a simple object that doesn't have getMetadata
		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) method.invoke(provider, "just a string");
		assertNotNull(result.get("value"));
	}

}
