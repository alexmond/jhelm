package org.alexmond.jhelm.core.exception;

import java.util.List;

import org.alexmond.jhelm.core.model.ResourceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionTest {

	@Test
	void testJhelmExceptionMessage() {
		JhelmException ex = new JhelmException("test error");
		assertEquals("test error", ex.getMessage());
	}

	@Test
	void testJhelmExceptionWithCause() {
		RuntimeException cause = new RuntimeException("root");
		JhelmException ex = new JhelmException("wrapped", cause);
		assertEquals("wrapped", ex.getMessage());
		assertEquals(cause, ex.getCause());
	}

	@Test
	void testReleaseNotFoundForRelease() {
		ReleaseNotFoundException ex = ReleaseNotFoundException.forRelease("myapp");
		assertEquals("Release not found: myapp", ex.getMessage());
	}

	@Test
	void testReleaseNotFoundForReleaseWithNamespace() {
		ReleaseNotFoundException ex = ReleaseNotFoundException.forRelease("myapp", "prod");
		assertEquals("Release not found: myapp in namespace prod", ex.getMessage());
	}

	@Test
	void testReleaseNotFoundForRevision() {
		ReleaseNotFoundException ex = ReleaseNotFoundException.forRevision("myapp", 3);
		assertEquals("Revision 3 not found for release myapp", ex.getMessage());
	}

	@Test
	void testReleaseStorageException() {
		RuntimeException cause = new RuntimeException("io");
		ReleaseStorageException ex = new ReleaseStorageException("store failed", cause);
		assertEquals("store failed", ex.getMessage());
		assertEquals(cause, ex.getCause());
	}

	@Test
	void testKubernetesOperationExceptionTransient() {
		KubernetesOperationException ex = new KubernetesOperationException("err", null, 500);
		assertTrue(ex.isTransient());
		assertEquals(500, ex.getStatusCode());
	}

	@Test
	void testKubernetesOperationExceptionNonTransient() {
		KubernetesOperationException ex = new KubernetesOperationException("err", null, 404);
		assertFalse(ex.isTransient());
	}

	@Test
	void testKubernetesOperationException429IsTransient() {
		KubernetesOperationException ex = new KubernetesOperationException("rate limited", null, 429);
		assertTrue(ex.isTransient());
	}

	@Test
	void testKubernetesOperationExceptionNoStatusCode() {
		KubernetesOperationException ex = new KubernetesOperationException("generic");
		assertEquals(-1, ex.getStatusCode());
		assertFalse(ex.isTransient());
	}

	@Test
	void testDeploymentFailedException() {
		RuntimeException cause = new RuntimeException("store failed");
		DeploymentFailedException ex = new DeploymentFailedException("deploy failed", cause, "yaml-manifest");
		assertEquals("deploy failed", ex.getMessage());
		assertEquals(cause, ex.getCause());
		assertEquals("yaml-manifest", ex.getAppliedManifest());
	}

	@Test
	void testDeploymentFailedExceptionNullManifest() {
		DeploymentFailedException ex = new DeploymentFailedException("deploy failed", null, null);
		assertNull(ex.getAppliedManifest());
	}

	@Test
	void testWaitTimeoutException() {
		List<ResourceStatus> pending = List.of(ResourceStatus.builder()
			.kind("Deployment")
			.name("app")
			.namespace("default")
			.ready(false)
			.message("0/3 replicas ready")
			.build());
		WaitTimeoutException ex = new WaitTimeoutException("timeout", pending);
		assertEquals("timeout", ex.getMessage());
		assertEquals(1, ex.getPendingResources().size());
		assertEquals("app", ex.getPendingResources().get(0).getName());
	}

}
