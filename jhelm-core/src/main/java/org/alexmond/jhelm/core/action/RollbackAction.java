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
public class RollbackAction {

	private final KubeService kubeService;

	/**
	 * Rolls a release back to a previous revision, optionally skipping its pre-rollback
	 * and post-rollback hooks and pruning old revision history. After the new revision is
	 * stored, prunes the release's revision history to the newest {@code maxHistory}
	 * revisions.
	 * @param options the rollback options (release name, namespace, target revision,
	 * no-hooks flag and the history cap)
	 */
	public void rollback(RollbackOptions options) {
		String name = options.getReleaseName();
		String namespace = options.getNamespace();
		int revision = options.getRevision();
		boolean noHooks = options.isNoHooks();
		int maxHistory = options.getMaxHistory();
		List<Release> history = kubeService.getReleaseHistory(name, namespace);
		Optional<Release> targetReleaseOpt = history.stream().filter((r) -> r.getVersion() == revision).findFirst();

		if (targetReleaseOpt.isEmpty()) {
			throw ReleaseNotFoundException.forRevision(name, revision);
		}

		Optional<Release> currentReleaseOpt = history.stream().findFirst(); // History is
																			// sorted
																			// descending
		int nextRevision = currentReleaseOpt.map((r) -> r.getVersion() + 1).orElse(1);

		Release targetRelease = targetReleaseOpt.get();
		Release newRelease = Release.builder()
			.name(targetRelease.getName())
			.namespace(targetRelease.getNamespace())
			.version(nextRevision)
			.chart(targetRelease.getChart())
			.manifest(targetRelease.getManifest())
			.info(Release.ReleaseInfo.builder()
				.firstDeployed(targetRelease.getInfo().getFirstDeployed())
				.lastDeployed(OffsetDateTime.now())
				.status(ReleaseStatus.DEPLOYED)
				.description("Rollback to " + revision)
				.build())
			.build();

		String manifest = newRelease.getManifest();
		String regularManifest = HookParser.stripHooks(manifest);
		List<HelmHook> hooks = noHooks ? List.of() : HookParser.parseHooks(manifest);
		HookExecutor hookExecutor = noHooks ? null : new HookExecutor(kubeService);
		if (!noHooks) {
			runHooks(hookExecutor, namespace, hooks, "pre-rollback");
		}
		kubeService.apply(namespace, regularManifest);
		kubeService.storeRelease(newRelease);
		kubeService.pruneReleaseHistory(name, namespace, maxHistory);
		if (!noHooks) {
			runHooks(hookExecutor, namespace, hooks, "post-rollback");
		}
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
