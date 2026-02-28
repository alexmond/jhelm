package org.alexmond.jhelm.core.util;

import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.service.KubeService;

/**
 * Executes Helm hooks for a given lifecycle phase.
 */
@Slf4j
@RequiredArgsConstructor
public class HookExecutor {

	private final KubeService kubeService;

	/**
	 * Runs all hooks matching the given phase in weight order.
	 * @param namespace the target namespace
	 * @param hooks all parsed hooks for this release
	 * @param phase the lifecycle phase (e.g. {@code pre-install})
	 * @param timeoutSeconds how long to wait for each hook to become ready
	 * @throws Exception if a hook apply or wait fails
	 */
	public void run(String namespace, List<HelmHook> hooks, String phase, int timeoutSeconds) throws Exception {
		List<HelmHook> phaseHooks = hooks.stream()
			.filter((h) -> h.getPhases().contains(phase))
			.sorted(Comparator.comparingInt(HelmHook::getWeight))
			.toList();

		for (HelmHook hook : phaseHooks) {
			List<String> policy = hook.getDeletePolicy();
			boolean defaultPolicy = policy == null || policy.isEmpty();

			if (defaultPolicy || policy.contains("before-hook-creation")) {
				try {
					kubeService.delete(namespace, hook.getYaml());
				}
				catch (Exception ex) {
					if (log.isDebugEnabled()) {
						log.debug("Pre-creation delete of hook {}/{} failed (ignored): {}", hook.getKind(),
								hook.getName(), ex.getMessage());
					}
				}
			}

			kubeService.apply(namespace, hook.getYaml());
			kubeService.waitForReady(namespace, hook.getYaml(), timeoutSeconds);

			if (policy != null && policy.contains("hook-succeeded")) {
				kubeService.delete(namespace, hook.getYaml());
			}
		}
	}

}
