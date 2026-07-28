package org.alexmond.jhelm.kube.service.internal;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.CascadePolicy;
import org.alexmond.jhelm.core.service.KubeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NamespaceScopedReleasesKubeService}: {@code listAllReleases()} is
 * scoped to the configured namespaces via the delegate's {@code listReleases(Set)} — the
 * cluster-wide {@code listAllReleases()} is never called — and every other method
 * forwards unchanged.
 */
class NamespaceScopedReleasesKubeServiceTest {

	private final KubeService delegate = mock(KubeService.class);

	private final NamespaceScopedReleasesKubeService service = new NamespaceScopedReleasesKubeService(this.delegate,
			Set.of("a", "b"));

	@Test
	void listAllReleasesDelegatesToNamespaceScopedListing() {
		Release ra = mock(Release.class);
		Release rb = mock(Release.class);
		// The real default listReleases(Set) composes per-namespace listReleases(String).
		when(this.delegate.listReleases(Set.of("a", "b"))).thenReturn(List.of(ra, rb));

		List<Release> result = this.service.listAllReleases();

		assertEquals(List.of(ra, rb), result);
		verify(this.delegate).listReleases(Set.of("a", "b"));
		// Cluster-wide listing (needs cluster-wide RBAC) must never be used.
		verify(this.delegate, never()).listAllReleases();
	}

	@Test
	void listAllReleasesReturnsUnionAcrossNamespacesWithDefaultComposition() {
		// Exercise the KubeService default listReleases(Set) end-to-end: no stub for the
		// Set overload, so the real default flattens per-namespace results.
		KubeService realDefault = new KubeService() {
			@Override
			public List<Release> listReleases(String namespace) {
				Release r = mock(Release.class);
				when(r.getName()).thenReturn("rel-" + namespace);
				return List.of(r);
			}

			@Override
			public void storeRelease(Release release) {
			}

			@Override
			public Optional<Release> getRelease(String name, String namespace) {
				return Optional.empty();
			}

			@Override
			public List<Release> listAllReleases() {
				throw new AssertionError("cluster-wide listAllReleases must not be called");
			}

			@Override
			public List<Release> getReleaseHistory(String name, String namespace) {
				return List.of();
			}

			@Override
			public void deleteReleaseHistory(String name, String namespace) {
			}

			@Override
			public void pruneReleaseHistory(String name, String namespace, int maxHistory) {
			}

			@Override
			public void ensureNamespace(String namespace) {
			}

			@Override
			public void apply(String namespace, String yamlContent) {
			}

			@Override
			public void delete(String namespace, String yamlContent) {
			}

			@Override
			public List<ResourceStatus> getResourceStatuses(String namespace, String manifest) {
				return List.of();
			}

			@Override
			public void waitForReady(String namespace, String manifest, int timeoutSeconds) {
			}
		};
		NamespaceScopedReleasesKubeService scoped = new NamespaceScopedReleasesKubeService(realDefault,
				Set.of("a", "b"));

		List<String> names = scoped.listAllReleases().stream().map(Release::getName).sorted().toList();

		assertEquals(List.of("rel-a", "rel-b"), names);
	}

	@Test
	void readOperationsForwardToDelegate() {
		Release release = mock(Release.class);
		when(this.delegate.getRelease("r", "ns")).thenReturn(Optional.of(release));
		when(this.delegate.listReleases("ns")).thenReturn(List.of(release));
		when(this.delegate.listReleases(Set.of("x"))).thenReturn(List.of(release));
		when(this.delegate.getReleaseHistory("r", "ns")).thenReturn(List.of(release));
		when(this.delegate.getResourceStatuses("ns", "m")).thenReturn(List.of());
		when(this.delegate.getCapabilities()).thenReturn(Capabilities.DEFAULT);

		assertEquals(Optional.of(release), this.service.getRelease("r", "ns"));
		assertEquals(List.of(release), this.service.listReleases("ns"));
		assertEquals(List.of(release), this.service.listReleases(Set.of("x")));
		assertEquals(List.of(release), this.service.getReleaseHistory("r", "ns"));
		assertEquals(List.of(), this.service.getResourceStatuses("ns", "m"));
		assertSame(Capabilities.DEFAULT, this.service.getCapabilities());
	}

	@Test
	void mutatingOperationsForwardToDelegate() {
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
	void waitOperationsForwardToDelegate() {
		this.service.waitForDeleted("ns", "m", 30);
		this.service.waitForReady("ns", "m", 30);
		this.service.waitForReady("ns", "m", 30, true);

		verify(this.delegate).waitForDeleted("ns", "m", 30);
		verify(this.delegate).waitForReady("ns", "m", 30);
		verify(this.delegate).waitForReady("ns", "m", 30, true);
		// listAllReleases was never invoked in these forwarding paths.
		verify(this.delegate, never()).listReleases(anyString());
	}

}
