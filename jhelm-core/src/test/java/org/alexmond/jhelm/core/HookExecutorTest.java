package org.alexmond.jhelm.core;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HookExecutorTest {

	@Mock
	private KubeService kubeService;

	private HookExecutor hookExecutor;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		hookExecutor = new HookExecutor(kubeService);
	}

	private HelmHook hookWithWeight(String name, String phase, int weight, List<String> policy) {
		return HelmHook.builder()
			.kind("Job")
			.name(name)
			.namespace("default")
			.phases(List.of(phase))
			.weight(weight)
			.deletePolicy(policy)
			.yaml("---\nkind: Job\nmetadata:\n  name: " + name)
			.build();
	}

	@Test
	void testHooksSortedByWeightAscending() throws Exception {
		HelmHook heavy = hookWithWeight("heavy-hook", "pre-install", 10, Collections.emptyList());
		HelmHook light = hookWithWeight("light-hook", "pre-install", 1, Collections.emptyList());

		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());

		hookExecutor.run("default", List.of(heavy, light), "pre-install", 300);

		InOrder order = inOrder(kubeService);
		order.verify(kubeService).apply("default", light.getYaml());
		order.verify(kubeService).apply("default", heavy.getYaml());
	}

	@Test
	void testHooksFilteredByPhase() throws Exception {
		HelmHook preInstall = hookWithWeight("pre-hook", "pre-install", 0, Collections.emptyList());
		HelmHook postInstall = hookWithWeight("post-hook", "post-install", 0, Collections.emptyList());

		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());

		hookExecutor.run("default", List.of(preInstall, postInstall), "pre-install", 300);

		verify(kubeService).apply("default", preInstall.getYaml());
		verify(kubeService, never()).apply("default", postInstall.getYaml());
	}

	@Test
	void testBeforeHookCreationCallsDeleteBeforeApply() throws Exception {
		HelmHook hook = hookWithWeight("my-hook", "pre-install", 0, List.of("before-hook-creation"));

		doNothing().when(kubeService).delete(anyString(), anyString());
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());

		hookExecutor.run("default", List.of(hook), "pre-install", 300);

		InOrder order = inOrder(kubeService);
		order.verify(kubeService).delete("default", hook.getYaml());
		order.verify(kubeService).apply("default", hook.getYaml());
	}

	@Test
	void testDefaultPolicyCallsDeleteBeforeApply() throws Exception {
		HelmHook hook = hookWithWeight("my-hook", "pre-install", 0, Collections.emptyList());

		doNothing().when(kubeService).delete(anyString(), anyString());
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());

		hookExecutor.run("default", List.of(hook), "pre-install", 300);

		verify(kubeService).delete("default", hook.getYaml());
		verify(kubeService).apply("default", hook.getYaml());
	}

	@Test
	void testHookSucceededCallsDeleteAfterWait() throws Exception {
		HelmHook hook = hookWithWeight("my-hook", "pre-install", 0, List.of("hook-succeeded"));

		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());
		doNothing().when(kubeService).delete(anyString(), anyString());

		hookExecutor.run("default", List.of(hook), "pre-install", 300);

		InOrder order = inOrder(kubeService);
		order.verify(kubeService).apply("default", hook.getYaml());
		order.verify(kubeService).waitForReady(eq("default"), eq(hook.getYaml()), eq(300));
		order.verify(kubeService).delete("default", hook.getYaml());
	}

	@Test
	void testPreCreationDeleteExceptionIsSwallowed() throws Exception {
		HelmHook hook = hookWithWeight("my-hook", "pre-install", 0, List.of("before-hook-creation"));

		doThrow(new RuntimeException("not found")).when(kubeService).delete(anyString(), anyString());
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());

		hookExecutor.run("default", List.of(hook), "pre-install", 300);

		verify(kubeService).apply("default", hook.getYaml());
	}

	@Test
	void testNoHooksForPhaseNoInteraction() throws Exception {
		HelmHook hook = hookWithWeight("post-hook", "post-install", 0, Collections.emptyList());

		hookExecutor.run("default", List.of(hook), "pre-install", 300);

		verify(kubeService, never()).apply(anyString(), anyString());
		verify(kubeService, never()).delete(anyString(), anyString());
		verify(kubeService, never()).waitForReady(anyString(), anyString(), anyInt());
	}

	@Test
	void testEmptyHooksListNoInteraction() throws Exception {
		hookExecutor.run("default", Collections.emptyList(), "pre-install", 300);

		verify(kubeService, never()).apply(anyString(), anyString());
	}

	@Test
	void testWaitForReadyCalledWithTimeoutSeconds() throws Exception {
		HelmHook hook = hookWithWeight("my-hook", "pre-install", 0, Collections.emptyList());

		doNothing().when(kubeService).delete(anyString(), anyString());
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());

		hookExecutor.run("default", List.of(hook), "pre-install", 120);

		verify(kubeService).waitForReady("default", hook.getYaml(), 120);
	}

	@Test
	void testBothBeforeHookCreationAndHookSucceeded() throws Exception {
		HelmHook hook = hookWithWeight("my-hook", "pre-install", 0, List.of("before-hook-creation", "hook-succeeded"));

		doNothing().when(kubeService).delete(anyString(), anyString());
		doNothing().when(kubeService).apply(anyString(), anyString());
		doNothing().when(kubeService).waitForReady(anyString(), anyString(), anyInt());

		hookExecutor.run("default", List.of(hook), "pre-install", 300);

		// delete called twice: before creation and after success
		verify(kubeService, times(2)).delete("default", hook.getYaml());
	}

}
