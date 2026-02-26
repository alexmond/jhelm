package org.alexmond.jhelm.core.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.ReleaseNotFoundException;
import org.alexmond.jhelm.core.exception.WaitTimeoutException;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.util.HookParser;

/**
 * Runs test hooks for a Helm release. Test hooks are Kubernetes resources annotated with
 * {@code helm.sh/hook: test} and are executed on demand.
 */
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({ "PMD.TestClassWithoutTestCases", "PMD.UnitTestShouldUseTestAnnotation" })
public class TestAction {

	private final KubeService kubeService;

	/**
	 * Runs all test hooks for a release and returns results.
	 * @param releaseName the release name
	 * @param namespace the release namespace
	 * @param timeoutSeconds timeout for each test hook
	 * @return list of test results
	 * @throws ReleaseNotFoundException if the release does not exist
	 * @throws Exception if a Kubernetes API error occurs
	 */
	public List<TestResult> test(String releaseName, String namespace, int timeoutSeconds) throws Exception {
		Optional<Release> releaseOpt = kubeService.getRelease(releaseName, namespace);
		if (releaseOpt.isEmpty()) {
			throw ReleaseNotFoundException.forRelease(releaseName, namespace);
		}

		Release release = releaseOpt.get();
		List<HelmHook> hooks = HookParser.parseHooks(release.getManifest());
		List<HelmHook> testHooks = hooks.stream()
			.filter((h) -> h.getPhases().contains("test"))
			.sorted((a, b) -> Integer.compare(a.getWeight(), b.getWeight()))
			.toList();

		List<TestResult> results = new ArrayList<>();

		for (HelmHook hook : testHooks) {
			TestResult result = runTestHook(hook, namespace, timeoutSeconds);
			results.add(result);
		}

		return results;
	}

	private TestResult runTestHook(HelmHook hook, String namespace, int timeoutSeconds) {
		TestResult result = TestResult.builder().kind(hook.getKind()).name(hook.getName()).hook(hook).build();

		try {
			// Apply the test hook resource
			kubeService.apply(namespace, hook.getYaml());

			// Wait for completion
			kubeService.waitForReady(namespace, hook.getYaml(), timeoutSeconds);
			result.setStatus(TestStatus.PASSED);
			log.info("Test hook {}/{} passed", hook.getKind(), hook.getName());

			// Clean up if delete policy includes hook-succeeded
			if (hook.getDeletePolicy() != null && hook.getDeletePolicy().contains("hook-succeeded")) {
				kubeService.delete(namespace, hook.getYaml());
			}
		}
		catch (WaitTimeoutException ex) {
			result.setStatus(TestStatus.FAILED);
			result.setMessage("Timeout waiting for test to complete");
			result.setPendingResources(ex.getPendingResources());
			log.warn("Test hook {}/{} timed out", hook.getKind(), hook.getName());
		}
		catch (Exception ex) {
			result.setStatus(TestStatus.FAILED);
			result.setMessage(ex.getMessage());
			log.warn("Test hook {}/{} failed: {}", hook.getKind(), hook.getName(), ex.getMessage());
		}

		return result;
	}

	/**
	 * Fetches container logs for a test hook resource.
	 * @param namespace the namespace
	 * @param hook the test hook
	 * @return log output, or empty string if unavailable
	 */
	public String getLogs(String namespace, HelmHook hook) {
		try {
			List<ResourceStatus> statuses = kubeService.getResourceStatuses(namespace, hook.getYaml());
			StringBuilder sb = new StringBuilder();
			for (ResourceStatus status : statuses) {
				sb.append(status.getKind())
					.append('/')
					.append(status.getName())
					.append(": ")
					.append(status.getMessage())
					.append('\n');
			}
			return sb.toString();
		}
		catch (Exception ex) {
			log.debug("Failed to get logs for {}/{}: {}", hook.getKind(), hook.getName(), ex.getMessage());
			return "";
		}
	}

	/**
	 * Result of a single test hook execution.
	 */
	@lombok.Data
	@lombok.Builder
	@lombok.NoArgsConstructor
	@lombok.AllArgsConstructor
	public static class TestResult {

		private String kind;

		private String name;

		@lombok.Builder.Default
		private TestStatus status = TestStatus.RUNNING;

		private String message;

		private List<ResourceStatus> pendingResources;

		private HelmHook hook;

	}

	/**
	 * Test execution status.
	 */
	public enum TestStatus {

		RUNNING, PASSED, FAILED

	}

}
