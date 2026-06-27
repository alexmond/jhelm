package org.alexmond.jhelm.core.action;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.alexmond.jhelm.core.exception.ReleaseNotFoundException;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.util.HookExecutor;
import org.alexmond.jhelm.core.util.HookParser;

@RequiredArgsConstructor
@Slf4j
public class UninstallAction {

	private final KubeService kubeService;

	/**
	 * Uninstalls a release, running its pre-delete and post-delete hooks.
	 * @param releaseName the name of the release to remove
	 * @param namespace the namespace the release lives in
	 */
	public void uninstall(String releaseName, String namespace) {
		uninstall(releaseName, namespace, false);
	}

	/**
	 * Uninstalls a release, optionally skipping lifecycle hooks.
	 * @param releaseName the name of the release to remove
	 * @param namespace the namespace the release lives in
	 * @param noHooks if {@code true}, skip running pre-delete and post-delete hooks
	 */
	public void uninstall(String releaseName, String namespace, boolean noHooks) {
		Optional<Release> releaseOpt = kubeService.getRelease(releaseName, namespace);
		if (releaseOpt.isEmpty()) {
			throw ReleaseNotFoundException.forRelease(releaseName, namespace);
		}

		Release release = releaseOpt.get();
		String regularManifest = HookParser.stripHooks(release.getManifest());
		List<HelmHook> hooks = noHooks ? List.of() : HookParser.parseHooks(release.getManifest());
		HookExecutor hookExecutor = noHooks ? null : new HookExecutor(kubeService);
		if (!noHooks) {
			runHooks(hookExecutor, namespace, hooks, "pre-delete");
		}
		String deletableManifest = HookParser.stripKeptResources(regularManifest);
		kubeService.delete(namespace, deletableManifest);
		if (!noHooks) {
			runHooks(hookExecutor, namespace, hooks, "post-delete");
		}
		kubeService.deleteReleaseHistory(releaseName, namespace);
	}

	private void runHooks(HookExecutor hookExecutor, String namespace, List<HelmHook> hooks, String phase) {
		try {
			hookExecutor.run(namespace, hooks, phase, 300);
		}
		catch (Exception ex) {
			throw new JhelmException("Failed to run " + phase + " hooks", ex);
		}
	}

}
