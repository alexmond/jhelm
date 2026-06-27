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
	 * Uninstalls a release, optionally skipping its pre-delete and post-delete hooks.
	 * @param options the uninstall options (release name, namespace and no-hooks flag)
	 */
	public void uninstall(UninstallOptions options) {
		String releaseName = options.getReleaseName();
		String namespace = options.getNamespace();
		boolean noHooks = options.isNoHooks();
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
