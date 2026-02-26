package org.alexmond.jhelm.kube.service;

import java.util.List;
import java.util.Optional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.KubeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservableKubeServiceTest {

	private KubeService delegate;

	private SimpleMeterRegistry registry;

	private JhelmMetrics metrics;

	private ObservableKubeService service;

	@BeforeEach
	void setUp() {
		delegate = mock(KubeService.class);
		registry = new SimpleMeterRegistry();
		metrics = new JhelmMetrics(registry);
		service = new ObservableKubeService(delegate, metrics);
	}

	@Test
	void testApplyRecordsTimerAndSuccess() throws Exception {
		service.apply("default", "apiVersion: v1");
		verify(delegate).apply("default", "apiVersion: v1");
		Timer timer = registry.find("jhelm.kube.operation").tag("operation", "apply").timer();
		assertNotNull(timer);
		assertEquals(1, timer.count());
		assertSuccessCount(1);
	}

	@Test
	void testApplyRecordsErrorOnFailure() throws Exception {
		doThrow(new RuntimeException("fail")).when(delegate).apply("default", "bad");
		assertThrows(RuntimeException.class, () -> service.apply("default", "bad"));
		assertErrorCount(1);
		Timer timer = registry.find("jhelm.kube.operation").tag("operation", "apply").timer();
		assertNotNull(timer);
		assertEquals(1, timer.count());
	}

	@Test
	void testDeleteRecordsTimer() throws Exception {
		service.delete("default", "yaml");
		verify(delegate).delete("default", "yaml");
		Timer timer = registry.find("jhelm.kube.operation").tag("operation", "delete").timer();
		assertNotNull(timer);
		assertEquals(1, timer.count());
	}

	@Test
	void testStoreReleaseRecordsTimer() throws Exception {
		Release release = mock(Release.class);
		service.storeRelease(release);
		verify(delegate).storeRelease(release);
		Timer timer = registry.find("jhelm.kube.operation").tag("operation", "store").timer();
		assertNotNull(timer);
		assertEquals(1, timer.count());
	}

	@Test
	void testGetReleaseRecordsTimer() throws Exception {
		when(delegate.getRelease("app", "default")).thenReturn(Optional.empty());
		Optional<Release> result = service.getRelease("app", "default");
		verify(delegate).getRelease("app", "default");
		assertTrue(result.isEmpty());
		Timer timer = registry.find("jhelm.kube.operation").tag("operation", "get").timer();
		assertNotNull(timer);
		assertEquals(1, timer.count());
	}

	@Test
	void testListReleasesRecordsTimer() throws Exception {
		when(delegate.listReleases("default")).thenReturn(List.of());
		List<Release> result = service.listReleases("default");
		verify(delegate).listReleases("default");
		assertTrue(result.isEmpty());
		Timer timer = registry.find("jhelm.kube.operation").tag("operation", "list").timer();
		assertNotNull(timer);
		assertEquals(1, timer.count());
	}

	@Test
	void testGetReleaseHistoryRecordsTimer() throws Exception {
		when(delegate.getReleaseHistory("app", "default")).thenReturn(List.of());
		List<Release> result = service.getReleaseHistory("app", "default");
		verify(delegate).getReleaseHistory("app", "default");
		assertTrue(result.isEmpty());
		Timer timer = registry.find("jhelm.kube.operation").tag("operation", "history").timer();
		assertNotNull(timer);
		assertEquals(1, timer.count());
	}

	@Test
	void testDeleteReleaseHistoryRecordsTimer() throws Exception {
		service.deleteReleaseHistory("app", "default");
		verify(delegate).deleteReleaseHistory("app", "default");
		// deleteReleaseHistory uses the delete timer
		Timer timer = registry.find("jhelm.kube.operation").tag("operation", "delete").timer();
		assertNotNull(timer);
		assertTrue(timer.count() >= 1);
	}

	@Test
	void testGetResourceStatusesRecordsTimer() throws Exception {
		when(delegate.getResourceStatuses("default", "manifest")).thenReturn(List.of());
		List<ResourceStatus> result = service.getResourceStatuses("default", "manifest");
		verify(delegate).getResourceStatuses("default", "manifest");
		assertTrue(result.isEmpty());
		Timer timer = registry.find("jhelm.kube.operation").tag("operation", "get").timer();
		assertNotNull(timer);
		assertTrue(timer.count() >= 1);
	}

	@Test
	void testWaitForReadyDelegatesToDelegate() throws Exception {
		service.waitForReady("default", "manifest", 30);
		verify(delegate).waitForReady("default", "manifest", 30);
	}

	@Test
	void testMultipleOperationsAccumulateCounters() throws Exception {
		when(delegate.getRelease("app", "default")).thenReturn(Optional.empty());
		service.apply("default", "yaml");
		service.getRelease("app", "default");
		service.apply("default", "yaml2");
		assertSuccessCount(3);
	}

	@Test
	void testMixedSuccessAndErrorCounters() throws Exception {
		service.apply("default", "yaml");
		doThrow(new RuntimeException("fail")).when(delegate).delete("default", "bad");
		assertThrows(RuntimeException.class, () -> service.delete("default", "bad"));
		assertSuccessCount(1);
		assertErrorCount(1);
	}

	private void assertSuccessCount(double expected) {
		Counter counter = registry.find("jhelm.kube.operations").tag("outcome", "success").counter();
		assertNotNull(counter);
		assertEquals(expected, counter.count());
	}

	private void assertErrorCount(double expected) {
		Counter counter = registry.find("jhelm.kube.operations").tag("outcome", "error").counter();
		assertNotNull(counter);
		assertEquals(expected, counter.count());
	}

}
