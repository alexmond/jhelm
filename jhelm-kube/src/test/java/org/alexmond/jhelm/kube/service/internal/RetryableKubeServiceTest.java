package org.alexmond.jhelm.kube.service.internal;

import java.net.ConnectException;
import java.net.SocketException;
import java.util.List;
import java.util.Optional;

import io.kubernetes.client.openapi.ApiException;
import org.alexmond.jhelm.core.exception.KubernetesOperationException;
import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.KubeService;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetryableKubeServiceTest {

	@Mock
	private KubeService delegate;

	private RetryableKubeService retryableService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);

		// Up to 3 attempts (2 retries) with no backoff for fast tests
		RetryPolicy policy = RetryPolicy.builder().maxRetries(2).delay(Duration.ZERO).build();
		RetryTemplate template = new RetryTemplate(policy);

		retryableService = new RetryableKubeService(delegate, template);
	}

	@Test
	void testDelegatesSuccessfulCalls() throws Exception {
		when(delegate.getRelease("app", "default")).thenReturn(Optional.of(mock(Release.class)));
		when(delegate.listReleases("default")).thenReturn(List.of(mock(Release.class)));

		assertTrue(retryableService.getRelease("app", "default").isPresent());
		assertEquals(1, retryableService.listReleases("default").size());

		verify(delegate).getRelease("app", "default");
		verify(delegate).listReleases("default");
	}

	@Test
	void testRetriesOnTransientException() throws Exception {
		when(delegate.listReleases("default")).thenThrow(new RuntimeException(new ApiException(500, "Server Error")))
			.thenThrow(new RuntimeException(new ApiException(503, "Unavailable")))
			.thenReturn(List.of());

		List<Release> result = retryableService.listReleases("default");
		assertTrue(result.isEmpty());
		verify(delegate, times(3)).listReleases("default");
	}

	@Test
	void testExhaustsRetriesAndThrows() throws Exception {
		when(delegate.listReleases("default")).thenThrow(new RuntimeException("fail 1"))
			.thenThrow(new RuntimeException("fail 2"))
			.thenThrow(new RuntimeException("fail 3"));

		assertThrows(RuntimeException.class, () -> retryableService.listReleases("default"));
		verify(delegate, times(3)).listReleases("default");
	}

	@Test
	void testApplyDelegatesToWrapped() throws Exception {
		retryableService.apply("default", "yaml-content");
		verify(delegate).apply("default", "yaml-content");
	}

	@Test
	void testGetCapabilitiesForwardsDelegateResult() {
		Capabilities caps = new Capabilities("v1.31.2", List.of());
		when(delegate.getCapabilities()).thenReturn(caps);

		assertEquals(caps, retryableService.getCapabilities());
		verify(delegate).getCapabilities();
	}

	@Test
	void testApplyDryRunDelegatesToWrapped() throws Exception {
		retryableService.applyDryRun("default", "yaml-content");
		verify(delegate).applyDryRun("default", "yaml-content");
	}

	@Test
	void testDeleteDelegatesToWrapped() throws Exception {
		retryableService.delete("default", "yaml-content");
		verify(delegate).delete("default", "yaml-content");
	}

	@Test
	void testStoreReleaseDelegatesToWrapped() throws Exception {
		Release release = mock(Release.class);
		retryableService.storeRelease(release);
		verify(delegate).storeRelease(release);
	}

	@Test
	void testGetReleaseHistoryDelegatesToWrapped() throws Exception {
		when(delegate.getReleaseHistory("app", "ns")).thenReturn(List.of());
		retryableService.getReleaseHistory("app", "ns");
		verify(delegate).getReleaseHistory("app", "ns");
	}

	@Test
	void testDeleteReleaseHistoryDelegatesToWrapped() throws Exception {
		retryableService.deleteReleaseHistory("app", "ns");
		verify(delegate).deleteReleaseHistory("app", "ns");
	}

	@Test
	void testGetResourceStatusesDelegatesToWrapped() throws Exception {
		when(delegate.getResourceStatuses("ns", "manifest")).thenReturn(List.of());
		retryableService.getResourceStatuses("ns", "manifest");
		verify(delegate).getResourceStatuses("ns", "manifest");
	}

	@Test
	void testWaitForReadyDelegatesDirectlyWithoutRetry() throws Exception {
		retryableService.waitForReady("ns", "manifest", 60);
		verify(delegate).waitForReady("ns", "manifest", 60);
	}

	// --- isTransient tests ---

	@Test
	void testIsTransientFor500ApiException() {
		assertTrue(RetryableKubeService.isTransient(new ApiException(500, "Internal")));
	}

	@Test
	void testIsTransientFor503ApiException() {
		assertTrue(RetryableKubeService.isTransient(new ApiException(503, "Unavailable")));
	}

	@Test
	void testIsTransientFor429ApiException() {
		assertTrue(RetryableKubeService.isTransient(new ApiException(429, "Too Many")));
	}

	@Test
	void testIsNotTransientFor404ApiException() {
		assertFalse(RetryableKubeService.isTransient(new ApiException(404, "Not Found")));
	}

	@Test
	void testIsNotTransientFor400ApiException() {
		assertFalse(RetryableKubeService.isTransient(new ApiException(400, "Bad Request")));
	}

	@Test
	void testIsTransientForKubernetesOperationException() {
		assertTrue(RetryableKubeService
			.isTransient(new KubernetesOperationException("err", new ApiException(502, ""), 502)));
	}

	@Test
	void testIsTransientForSocketException() {
		assertTrue(RetryableKubeService.isTransient(new SocketException("Connection reset")));
	}

	@Test
	void testIsTransientForConnectException() {
		assertTrue(RetryableKubeService.isTransient(new ConnectException("Connection refused")));
	}

	@Test
	void testIsTransientForWrappedCause() {
		assertTrue(RetryableKubeService.isTransient(new RuntimeException(new ApiException(500, "wrapped"))));
	}

	@Test
	void testIsNotTransientForGenericException() {
		assertFalse(RetryableKubeService.isTransient(new IllegalArgumentException("bad arg")));
	}

}
