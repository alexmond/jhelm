package org.alexmond.jhelm.core.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.Release;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DelegatingKubeServiceTest {

	private final KubeService delegate = mock(KubeService.class);

	private final DelegatingKubeService service = new DelegatingKubeService(() -> this.delegate);

	@Test
	void testResolverConsultedOncePerCall() {
		AtomicInteger resolveCount = new AtomicInteger();
		KubeService target = mock(KubeService.class);
		DelegatingKubeService counting = new DelegatingKubeService(() -> {
			resolveCount.incrementAndGet();
			return target;
		});
		counting.ensureNamespace("ns");
		counting.ensureNamespace("ns");
		assertEquals(2, resolveCount.get());
		verify(target, times(2)).ensureNamespace("ns");
	}

	@Test
	void testReadOperationsForwardAndReturn() {
		Release release = mock(Release.class);
		when(this.delegate.getRelease("r", "ns")).thenReturn(Optional.of(release));
		when(this.delegate.listReleases("ns")).thenReturn(List.of(release));
		when(this.delegate.listAllReleases()).thenReturn(List.of(release));
		when(this.delegate.getReleaseHistory("r", "ns")).thenReturn(List.of(release));
		when(this.delegate.getResourceStatuses("ns", "m")).thenReturn(List.of());
		when(this.delegate.getCapabilities()).thenReturn(Capabilities.DEFAULT);

		assertEquals(Optional.of(release), this.service.getRelease("r", "ns"));
		assertEquals(List.of(release), this.service.listReleases("ns"));
		assertEquals(List.of(release), this.service.listAllReleases());
		assertEquals(List.of(release), this.service.getReleaseHistory("r", "ns"));
		assertEquals(List.of(), this.service.getResourceStatuses("ns", "m"));
		assertSame(Capabilities.DEFAULT, this.service.getCapabilities());
	}

	@Test
	void testMutatingOperationsForward() {
		Release release = mock(Release.class);
		this.service.storeRelease(release);
		this.service.deleteReleaseHistory("r", "ns");
		this.service.pruneReleaseHistory("r", "ns", 3);
		this.service.ensureNamespace("ns");
		this.service.apply("ns", "yaml");
		this.service.applyDryRun("ns", "yaml");
		this.service.delete("ns", "yaml");
		this.service.delete("ns", "yaml", CascadePolicy.FOREGROUND);
		this.service.restartWorkloads("ns", "m");

		verify(this.delegate).storeRelease(release);
		verify(this.delegate).deleteReleaseHistory("r", "ns");
		verify(this.delegate).pruneReleaseHistory("r", "ns", 3);
		verify(this.delegate).ensureNamespace("ns");
		verify(this.delegate).apply("ns", "yaml");
		verify(this.delegate).applyDryRun("ns", "yaml");
		verify(this.delegate).delete("ns", "yaml");
		verify(this.delegate).delete("ns", "yaml", CascadePolicy.FOREGROUND);
		verify(this.delegate).restartWorkloads("ns", "m");
	}

	@Test
	void testWaitOperationsForward() {
		this.service.waitForDeleted("ns", "m", 30);
		this.service.waitForReady("ns", "m", 30);
		this.service.waitForReady("ns", "m", 30, true);

		verify(this.delegate).waitForDeleted("ns", "m", 30);
		verify(this.delegate).waitForReady("ns", "m", 30);
		verify(this.delegate).waitForReady("ns", "m", 30, true);
	}

}
