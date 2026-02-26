package org.alexmond.jhelm.core.action;

import java.util.List;
import java.util.Optional;

import org.alexmond.jhelm.core.action.TestAction.TestResult;
import org.alexmond.jhelm.core.action.TestAction.TestStatus;
import org.alexmond.jhelm.core.exception.ReleaseNotFoundException;
import org.alexmond.jhelm.core.exception.WaitTimeoutException;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.Release.ReleaseInfo;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.KubeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestActionTest {

	private KubeService kubeService;

	private TestAction testAction;

	private static final String TEST_MANIFEST = """
			---
			apiVersion: v1
			kind: Pod
			metadata:
			  name: myapp-test-connection
			  annotations:
			    "helm.sh/hook": test
			    "helm.sh/hook-weight": "1"
			    "helm.sh/hook-delete-policy": hook-succeeded
			spec:
			  containers:
			    - name: wget
			      image: busybox
			      command: ['wget']
			      args: ['myapp:8080']
			  restartPolicy: Never
			""";

	private static final String MULTI_TEST_MANIFEST = """
			---
			apiVersion: v1
			kind: Pod
			metadata:
			  name: myapp-test-first
			  annotations:
			    "helm.sh/hook": test
			    "helm.sh/hook-weight": "2"
			spec:
			  containers:
			    - name: test
			      image: busybox
			  restartPolicy: Never
			---
			apiVersion: v1
			kind: Pod
			metadata:
			  name: myapp-test-second
			  annotations:
			    "helm.sh/hook": test
			    "helm.sh/hook-weight": "1"
			spec:
			  containers:
			    - name: test
			      image: busybox
			  restartPolicy: Never
			""";

	private static final String NO_TEST_MANIFEST = """
			---
			apiVersion: v1
			kind: Pod
			metadata:
			  name: myapp-pre-install
			  annotations:
			    "helm.sh/hook": pre-install
			spec:
			  containers:
			    - name: job
			      image: busybox
			  restartPolicy: Never
			""";

	@BeforeEach
	void setUp() {
		kubeService = mock(KubeService.class);
		testAction = new TestAction(kubeService);
	}

	@Test
	void testPassingHook() throws Exception {
		Release release = buildRelease(TEST_MANIFEST);
		when(kubeService.getRelease("myapp", "default")).thenReturn(Optional.of(release));
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());

		List<TestResult> results = testAction.test("myapp", "default", 300);

		assertEquals(1, results.size());
		assertEquals(TestStatus.PASSED, results.get(0).getStatus());
		assertEquals("Pod", results.get(0).getKind());
		assertEquals("myapp-test-connection", results.get(0).getName());
		assertNotNull(results.get(0).getHook());
	}

	@Test
	void testCleanupOnSuccess() throws Exception {
		Release release = buildRelease(TEST_MANIFEST);
		when(kubeService.getRelease("myapp", "default")).thenReturn(Optional.of(release));
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());

		testAction.test("myapp", "default", 300);

		verify(kubeService).delete(eq("default"), anyString());
	}

	@Test
	void testFailingHookTimeout() throws Exception {
		Release release = buildRelease(TEST_MANIFEST);
		when(kubeService.getRelease("myapp", "default")).thenReturn(Optional.of(release));
		doNothing().when(kubeService).apply(anyString(), anyString());

		List<ResourceStatus> pending = List
			.of(ResourceStatus.builder().kind("Pod").name("myapp-test-connection").message("not ready").build());
		doThrow(new WaitTimeoutException("timeout", pending)).when(kubeService)
			.waitForReady(anyString(), anyString(), anyInt());

		List<TestResult> results = testAction.test("myapp", "default", 300);

		assertEquals(1, results.size());
		assertEquals(TestStatus.FAILED, results.get(0).getStatus());
		assertEquals("Timeout waiting for test to complete", results.get(0).getMessage());
		assertNotNull(results.get(0).getPendingResources());
		assertEquals(1, results.get(0).getPendingResources().size());
	}

	@Test
	void testFailingHookException() throws Exception {
		Release release = buildRelease(TEST_MANIFEST);
		when(kubeService.getRelease("myapp", "default")).thenReturn(Optional.of(release));
		doThrow(new RuntimeException("apply failed")).when(kubeService).apply(anyString(), anyString());

		List<TestResult> results = testAction.test("myapp", "default", 300);

		assertEquals(1, results.size());
		assertEquals(TestStatus.FAILED, results.get(0).getStatus());
		assertEquals("apply failed", results.get(0).getMessage());
	}

	@Test
	void testReleaseNotFound() throws Exception {
		when(kubeService.getRelease("missing", "default")).thenReturn(Optional.empty());

		assertThrows(ReleaseNotFoundException.class, () -> testAction.test("missing", "default", 300));
	}

	@Test
	void testNoTestHooks() throws Exception {
		Release release = buildRelease(NO_TEST_MANIFEST);
		when(kubeService.getRelease("myapp", "default")).thenReturn(Optional.of(release));

		List<TestResult> results = testAction.test("myapp", "default", 300);

		assertTrue(results.isEmpty());
		verify(kubeService, never()).apply(anyString(), anyString());
	}

	@Test
	void testMultipleHooksSortedByWeight() throws Exception {
		Release release = buildRelease(MULTI_TEST_MANIFEST);
		when(kubeService.getRelease("myapp", "default")).thenReturn(Optional.of(release));
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());

		List<TestResult> results = testAction.test("myapp", "default", 300);

		assertEquals(2, results.size());
		// Weight 1 should come first
		assertEquals("myapp-test-second", results.get(0).getName());
		assertEquals("myapp-test-first", results.get(1).getName());
	}

	@Test
	void testGetLogsReturnsStatuses() throws Exception {
		HelmHook hook = HelmHook.builder().kind("Pod").name("test-pod").yaml("apiVersion: v1\nkind: Pod").build();
		List<ResourceStatus> statuses = List
			.of(ResourceStatus.builder().kind("Pod").name("test-pod").message("Completed").build());
		when(kubeService.getResourceStatuses("default", hook.getYaml())).thenReturn(statuses);

		String logs = testAction.getLogs("default", hook);

		assertTrue(logs.contains("Pod/test-pod: Completed"));
	}

	@Test
	void testGetLogsReturnsEmptyOnError() throws Exception {
		HelmHook hook = HelmHook.builder().kind("Pod").name("test-pod").yaml("invalid").build();
		when(kubeService.getResourceStatuses(anyString(), anyString())).thenThrow(new RuntimeException("error"));

		String logs = testAction.getLogs("default", hook);

		assertEquals("", logs);
	}

	private Release buildRelease(String manifest) {
		return Release.builder()
			.name("myapp")
			.namespace("default")
			.version(1)
			.manifest(manifest)
			.info(ReleaseInfo.builder().status("deployed").build())
			.build();
	}

}
