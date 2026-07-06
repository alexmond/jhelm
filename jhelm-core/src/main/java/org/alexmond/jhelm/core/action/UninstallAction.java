package org.alexmond.jhelm.core.action;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
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

	@Setter
	private JhelmMetrics metrics;

	/**
	 * Uninstalls a release, optionally skipping its pre-delete and post-delete hooks.
	 * @param options the uninstall options (release name, namespace and no-hooks flag)
	 */
	public void uninstall(UninstallOptions options) {
		if (this.metrics == null) {
			doUninstall(options);
		}
		else {
			this.metrics.timeAction("uninstall", () -> {
				doUninstall(options);
				return null;
			});
		}
	}

	private void doUninstall(UninstallOptions options) {
		String releaseName = options.getReleaseName();
		String namespace = options.getNamespace();
		boolean noHooks = options.isNoHooks();
		Optional<Release> releaseOpt = kubeService.getRelease(releaseName, namespace);
		if (releaseOpt.isEmpty()) {
			throw ReleaseNotFoundException.forRelease(releaseName, namespace);
		}

		Release release = releaseOpt.get();
		String regularManifest = HookParser.stripHooks(release.getManifest());
		String deletableManifest = HookParser.stripKeptResources(regularManifest);
		// --dry-run: report what would happen without deleting resources or touching
		// history.
		if (options.isDryRun()) {
			log.info("--dry-run: would uninstall release {} in namespace {}", releaseName, namespace);
			return;
		}
		List<HelmHook> hooks = noHooks ? List.of() : HookParser.parseHooks(release.getManifest());
		HookExecutor hookExecutor = noHooks ? null : new HookExecutor(kubeService);
		if (!noHooks) {
			runHooks(hookExecutor, namespace, hooks, "pre-delete");
		}
		kubeService.delete(namespace, deletableManifest, options.getCascade());
		if (options.isWait()) {
			kubeService.waitForDeleted(namespace, deletableManifest, options.getTimeout());
		}
		if (!noHooks) {
			runHooks(hookExecutor, namespace, hooks, "post-delete");
		}
		if (options.isKeepHistory()) {
			kubeService.storeRelease(markUninstalled(release, options.getDescription()));
		}
		else {
			kubeService.deleteReleaseHistory(releaseName, namespace);
		}
	}

	private Release markUninstalled(Release release, String description) {
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
				.description((description != null && !description.isBlank()) ? description : "Uninstallation complete")
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
