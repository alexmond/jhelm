package org.alexmond.jhelm.core.service;

import java.util.List;
import java.util.Set;

import org.alexmond.jhelm.core.model.Release;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the default {@link KubeService#listReleases(Set)} composes the
 * namespace-scoped {@link KubeService#listReleases(String)} — flattening the
 * per-namespace results and never touching the cluster-wide
 * {@link KubeService#listAllReleases()} — so it works with only namespace-scoped list
 * permission.
 */
class KubeServiceNamespaceSetListingTest {

	private final KubeService service = mock(KubeService.class);

	@Test
	void listReleasesForNamespaceSetFlattensPerNamespaceResults() {
		Release ra = mock(Release.class);
		Release rb1 = mock(Release.class);
		Release rb2 = mock(Release.class);
		when(this.service.listReleases("a")).thenReturn(List.of(ra));
		when(this.service.listReleases("b")).thenReturn(List.of(rb1, rb2));
		when(this.service.listReleases(Set.of("a", "b"))).thenCallRealMethod();

		List<Release> result = this.service.listReleases(Set.of("a", "b"));

		assertEquals(3, result.size());
		verify(this.service).listReleases("a");
		verify(this.service).listReleases("b");
		// Cluster-wide listing (needs cluster-wide RBAC) is never invoked.
		verify(this.service, never()).listAllReleases();
	}

	@Test
	void listReleasesForEmptyNamespaceSetReturnsEmptyList() {
		when(this.service.listReleases(Set.<String>of())).thenCallRealMethod();

		assertEquals(List.of(), this.service.listReleases(Set.<String>of()));

		verify(this.service, never()).listReleases("a");
		verify(this.service, never()).listAllReleases();
	}

}
