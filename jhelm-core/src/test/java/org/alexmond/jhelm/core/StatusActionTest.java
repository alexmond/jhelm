package org.alexmond.jhelm.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class StatusActionTest {

	@Mock
	private KubeService kubeService;

	private StatusAction statusAction;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		statusAction = new StatusAction(kubeService);
	}

	@Test
	void testStatusReturnsRelease() throws Exception {
		Release mockRelease = Release.builder().name("test-release").namespace("default").version(1).build();

		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(mockRelease));

		Optional<Release> result = statusAction.status("test-release", "default");

		assertTrue(result.isPresent());
		assertEquals("test-release", result.get().getName());
	}

	@Test
	void testStatusReturnsEmpty() throws Exception {
		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

		Optional<Release> result = statusAction.status("non-existent", "default");

		assertFalse(result.isPresent());
	}

	@Test
	void testGetResourceStatusesReturnsStatuses() throws Exception {
		Release release = Release.builder()
			.name("test-release")
			.namespace("default")
			.version(1)
			.manifest("---\napiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: my-deploy\n")
			.build();

		List<ResourceStatus> expected = List.of(ResourceStatus.builder()
			.kind("Deployment")
			.name("my-deploy")
			.namespace("default")
			.ready(true)
			.message("ready")
			.build());

		when(kubeService.getResourceStatuses(anyString(), anyString())).thenReturn(expected);

		List<ResourceStatus> result = statusAction.getResourceStatuses(release);

		assertEquals(1, result.size());
		assertEquals("Deployment", result.get(0).getKind());
		assertTrue(result.get(0).isReady());
	}

	@Test
	void testGetResourceStatusesWithNullManifestReturnsEmpty() throws Exception {
		Release release = Release.builder().name("test-release").namespace("default").version(1).build();

		List<ResourceStatus> result = statusAction.getResourceStatuses(release);

		assertTrue(result.isEmpty());
	}

	@Test
	void testGetResourceStatusesWithBlankManifestReturnsEmpty() throws Exception {
		Release release = Release.builder().name("test-release").namespace("default").version(1).manifest("").build();

		List<ResourceStatus> result = statusAction.getResourceStatuses(release);

		assertTrue(result.isEmpty());
	}

}
