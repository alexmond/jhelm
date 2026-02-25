package org.alexmond.jhelm.core;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookParserTest {

	private static final String HOOK_DOC = """
			apiVersion: batch/v1
			kind: Job
			metadata:
			  name: pre-install-job
			  namespace: default
			  annotations:
			    helm.sh/hook: pre-install
			    helm.sh/hook-weight: "5"
			    helm.sh/hook-delete-policy: before-hook-creation,hook-succeeded
			spec:
			  template:
			    spec:
			      restartPolicy: Never
			""";

	private static final String REGULAR_DOC = """
			apiVersion: v1
			kind: ConfigMap
			metadata:
			  name: my-config
			  namespace: default
			data:
			  key: value
			""";

	@Test
	void testParseHooksReturnsEmptyForNull() {
		List<HelmHook> hooks = HookParser.parseHooks(null);
		assertTrue(hooks.isEmpty());
	}

	@Test
	void testParseHooksReturnsEmptyForBlank() {
		List<HelmHook> hooks = HookParser.parseHooks("   ");
		assertTrue(hooks.isEmpty());
	}

	@Test
	void testParseHooksReturnsEmptyForNoHooks() {
		String manifest = "---\n" + REGULAR_DOC;
		List<HelmHook> hooks = HookParser.parseHooks(manifest);
		assertTrue(hooks.isEmpty());
	}

	@Test
	void testParseHooksExtractsPhases() {
		String manifest = "---\n" + HOOK_DOC;
		List<HelmHook> hooks = HookParser.parseHooks(manifest);
		assertEquals(1, hooks.size());
		assertEquals(List.of("pre-install"), hooks.get(0).getPhases());
	}

	@Test
	void testParseHooksExtractsWeight() {
		String manifest = "---\n" + HOOK_DOC;
		List<HelmHook> hooks = HookParser.parseHooks(manifest);
		assertEquals(5, hooks.get(0).getWeight());
	}

	@Test
	void testParseHooksExtractsDeletePolicy() {
		String manifest = "---\n" + HOOK_DOC;
		List<HelmHook> hooks = HookParser.parseHooks(manifest);
		List<String> policy = hooks.get(0).getDeletePolicy();
		assertTrue(policy.contains("before-hook-creation"));
		assertTrue(policy.contains("hook-succeeded"));
	}

	@Test
	void testParseHooksExtractsKindAndName() {
		String manifest = "---\n" + HOOK_DOC;
		List<HelmHook> hooks = HookParser.parseHooks(manifest);
		assertEquals("Job", hooks.get(0).getKind());
		assertEquals("pre-install-job", hooks.get(0).getName());
	}

	@Test
	void testParseHooksExtractsNamespace() {
		String manifest = "---\n" + HOOK_DOC;
		List<HelmHook> hooks = HookParser.parseHooks(manifest);
		assertEquals("default", hooks.get(0).getNamespace());
	}

	@Test
	void testParseHooksIgnoresNonHookDocs() {
		String manifest = "---\n" + REGULAR_DOC + "---\n" + HOOK_DOC;
		List<HelmHook> hooks = HookParser.parseHooks(manifest);
		assertEquals(1, hooks.size());
	}

	@Test
	void testParseHooksMultiplePhases() {
		String multiPhasedHook = """
				apiVersion: batch/v1
				kind: Job
				metadata:
				  name: multi-hook
				  annotations:
				    helm.sh/hook: pre-install,post-install
				spec:
				  template:
				    spec:
				      restartPolicy: Never
				""";
		List<HelmHook> hooks = HookParser.parseHooks("---\n" + multiPhasedHook);
		assertEquals(1, hooks.size());
		assertEquals(List.of("pre-install", "post-install"), hooks.get(0).getPhases());
	}

	@Test
	void testParseHooksDefaultWeight() {
		String hookNoWeight = """
				apiVersion: batch/v1
				kind: Job
				metadata:
				  name: no-weight-hook
				  annotations:
				    helm.sh/hook: pre-install
				spec:
				  template:
				    spec:
				      restartPolicy: Never
				""";
		List<HelmHook> hooks = HookParser.parseHooks("---\n" + hookNoWeight);
		assertEquals(0, hooks.get(0).getWeight());
	}

	@Test
	void testParseHooksEmptyDeletePolicy() {
		String hookNoPolicy = """
				apiVersion: batch/v1
				kind: Job
				metadata:
				  name: no-policy-hook
				  annotations:
				    helm.sh/hook: pre-install
				spec:
				  template:
				    spec:
				      restartPolicy: Never
				""";
		List<HelmHook> hooks = HookParser.parseHooks("---\n" + hookNoPolicy);
		assertTrue(hooks.get(0).getDeletePolicy().isEmpty());
	}

	@Test
	void testParseHooksMultipleHooks() {
		String hook2 = """
				apiVersion: batch/v1
				kind: Job
				metadata:
				  name: post-install-job
				  annotations:
				    helm.sh/hook: post-install
				    helm.sh/hook-weight: "10"
				spec:
				  template:
				    spec:
				      restartPolicy: Never
				""";
		String manifest = "---\n" + HOOK_DOC + "---\n" + hook2;
		List<HelmHook> hooks = HookParser.parseHooks(manifest);
		assertEquals(2, hooks.size());
	}

	@Test
	void testStripHooksReturnsEmptyForNull() {
		String result = HookParser.stripHooks(null);
		assertEquals("", result);
	}

	@Test
	void testStripHooksReturnsBlankForBlank() {
		String result = HookParser.stripHooks("   ");
		assertEquals("   ", result);
	}

	@Test
	void testStripHooksRemovesHookDocs() {
		String manifest = "---\n" + REGULAR_DOC + "---\n" + HOOK_DOC;
		String stripped = HookParser.stripHooks(manifest);
		assertFalse(stripped.contains("helm.sh/hook"));
		assertTrue(stripped.contains("ConfigMap"));
	}

	@Test
	void testStripHooksPreservesNonHookDocs() {
		String manifest = "---\n" + REGULAR_DOC + "---\n" + HOOK_DOC;
		String stripped = HookParser.stripHooks(manifest);
		assertTrue(stripped.contains("my-config"));
	}

	@Test
	void testStripHooksOnlyHooks() {
		String manifest = "---\n" + HOOK_DOC;
		String stripped = HookParser.stripHooks(manifest);
		assertTrue(stripped.isBlank() || stripped.equals(""));
	}

}
