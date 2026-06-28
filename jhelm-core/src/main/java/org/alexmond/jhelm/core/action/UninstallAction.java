package org.alexmond.jhelm.core.action;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.alexmond.jhelm.core.exception.ReleaseNotFoundException;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
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
		if (options.isKeepHistory()) {
			kubeService.storeRelease(markUninstalled(release));
		}
		else {
			kubeService.deleteReleaseHistory(releaseName, namespace);
		}
	}

	private Release markUninstalled(Release release) {
		Release.ReleaseInfo previousInfo = release.getInfo();
		OffsetDateTime firstDeployed = (previousInfo != null) ? previousInfo.getFirstDeployed() : null;
		return Release.builder()
			.name(release.getName())
			.namespace(release.getNamespace())
			.version(release.getVersion())
			.chart(release.getChart())
			.manifest(release.getManifest())
			.info(Release.ReleaseInfo.builder()
				.firstDeployed(firstDeployed)
				.lastDeployed(OffsetDateTime.now())
				.status(ReleaseStatus.UNINSTALLED)
				.description("Uninstallation complete")
				.build())
			.build();
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
