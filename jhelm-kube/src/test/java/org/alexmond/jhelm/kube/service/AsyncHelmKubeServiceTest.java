package org.alexmond.jhelm.kube.service;

import io.kubernetes.client.openapi.ApiClient;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.AsyncKubeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link AsyncHelmKubeService}. Uses a spy on the service to verify that each
 * async method correctly delegates to its synchronous counterpart on a virtual thread.
 * <p>
 * Cross-thread mock-construction interception is not reliable with Mockito because
 * {@code mockConstruction} is thread-local; spy-based delegation testing is used instead.
 * </p>
 */
class AsyncHelmKubeServiceTest {

	@Mock
	private ApiClient apiClient;

	private AsyncHelmKubeService spyService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		spyService = spy(new AsyncHelmKubeService(apiClient));
	}

	private Release createTestRelease(String name, String namespace, int version) {
		return Release.builder()
			.name(name)
			.namespace(namespace)
			.version(version)
			.info(Release.ReleaseInfo.builder()
				.status("deployed")
				.firstDeployed(OffsetDateTime.now())
				.lastDeployed(OffsetDateTime.now())
				.description("Test release")
				.build())
			.build();
	}

	// --- implements AsyncKubeService ---

	@Test
	void asyncHelmKubeService_implementsAsyncKubeService() {
		assertTrue(spyService instanceof AsyncKubeService);
	}

	@Test
	void asyncHelmKubeService_isInstanceOfHelmKubeService() {
		assertTrue(spyService instanceof HelmKubeService);
	}

	// --- storeReleaseAsync ---

	@Test
	void storeReleaseAsync_delegatesToSyncMethod() throws Exception {
		Release release = createTestRelease("myapp", "default", 1);
		doAnswer((inv) -> null).when(spyService).storeRelease(any(Release.class));

		spyService.storeReleaseAsync(release).join();

		verify(spyService).storeRelease(release);
	}

	// --- getReleaseAsync ---

	@Test
	void getReleaseAsync_returnsPresent() throws Exception {
		Release release = createTestRelease("myapp", "default", 1);
		doReturn(Optional.of(release)).when(spyService).getRelease(eq("myapp"), eq("default"));

		Optional<Release> result = spyService.getReleaseAsync("myapp", "default").join();

		assertTrue(result.isPresent());
		verify(spyService).getRelease("myapp", "default");
	}

	@Test
	void getReleaseAsync_returnsEmpty() throws Exception {
		doReturn(Optional.empty()).when(spyService).getRelease(anyString(), anyString());

		Optional<Release> result = spyService.getReleaseAsync("missing", "default").join();

		assertFalse(result.isPresent());
	}

	// --- listReleasesAsync ---

	@Test
	void listReleasesAsync_delegatesToSyncMethod() throws Exception {
		List<Release> releases = List.of(createTestRelease("app1", "default", 1));
		doReturn(releases).when(spyService).listReleases(eq("default"));

		List<Release> result = spyService.listReleasesAsync("default").join();

		assertNotNull(result);
		assertFalse(result.isEmpty());
		verify(spyService).listReleases("default");
	}

	// --- getReleaseHistoryAsync ---

	@Test
	void getReleaseHistoryAsync_delegatesToSyncMethod() throws Exception {
		List<Release> history = List.of(createTestRelease("myapp", "default", 1),
				createTestRelease("myapp", "default", 2));
		doReturn(history).when(spyService).getReleaseHistory(eq("myapp"), eq("default"));

		List<Release> result = spyService.getReleaseHistoryAsync("myapp", "default").join();

		assertNotNull(result);
		verify(spyService).getReleaseHistory("myapp", "default");
	}

	// --- deleteReleaseHistoryAsync ---

	@Test
	void deleteReleaseHistoryAsync_delegatesToSyncMethod() throws Exception {
		doAnswer((inv) -> null).when(spyService).deleteReleaseHistory(anyString(), anyString());

		spyService.deleteReleaseHistoryAsync("myapp", "default").join();

		verify(spyService).deleteReleaseHistory("myapp", "default");
	}

	// --- deleteAsync ---

	@Test
	void deleteAsync_delegatesToSyncMethod() throws Exception {
		doAnswer((inv) -> null).when(spyService).delete(anyString(), anyString());
		String yaml = "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: test\n";

		spyService.deleteAsync("default", yaml).join();

		verify(spyService).delete("default", yaml);
	}

	// --- getResourceStatusesAsync ---

	@Test
	void getResourceStatusesAsync_delegatesToSyncMethod() throws Exception {
		List<ResourceStatus> statuses = List
			.of(ResourceStatus.builder().kind("ConfigMap").name("test").namespace("default").ready(true).build());
		String yaml = "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: test\n";
		doReturn(statuses).when(spyService).getResourceStatuses(anyString(), anyString());

		List<ResourceStatus> result = spyService.getResourceStatusesAsync("default", yaml).join();

		assertNotNull(result);
		verify(spyService).getResourceStatuses("default", yaml);
	}

	// --- waitForReadyAsync ---

	@Test
	void waitForReadyAsync_delegatesToSyncMethod() throws Exception {
		doAnswer((inv) -> null).when(spyService).waitForReady(anyString(), anyString(), any(int.class));
		String yaml = "apiVersion: v1\nkind: Service\nmetadata:\n  name: svc\n";

		spyService.waitForReadyAsync("default", yaml, 30).join();

		verify(spyService).waitForReady("default", yaml, 30);
	}

	// --- exception propagation ---

	@Test
	void applyAsync_propagatesExceptionAsCompletionException() throws Exception {
		doThrow(new RuntimeException("simulated failure")).when(spyService).apply(anyString(), anyString());
		String yaml = "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: test\n";

		CompletableFuture<Void> future = spyService.applyAsync("default", yaml);

		assertThrows(CompletionException.class, future::join);
	}

	@Test
	void storeReleaseAsync_propagatesExceptionAsCompletionException() throws Exception {
		doThrow(new RuntimeException("store failed")).when(spyService).storeRelease(any());

		CompletableFuture<Void> future = spyService.storeReleaseAsync(createTestRelease("app", "default", 1));

		assertThrows(CompletionException.class, future::join);
	}

}
