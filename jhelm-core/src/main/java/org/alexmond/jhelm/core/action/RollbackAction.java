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
public class RollbackAction {

	private final KubeService kubeService;

	@Setter
	private JhelmMetrics metrics;

	/**
	 * Rolls a release back to a previous revision, optionally skipping its pre-rollback
	 * and post-rollback hooks and pruning old revision history. After the new revision is
	 * stored, prunes the release's revision history to the newest {@code maxHistory}
	 * revisions.
	 * @param options the rollback options (release name, namespace, target revision,
	 * no-hooks flag and the history cap)
	 * @return the newly created {@link Release} representing the rolled-back revision
	 */
	public Release rollback(RollbackOptions options) {
		return (this.metrics == null) ? doRollback(options)
				: this.metrics.timeAction("rollback", () -> doRollback(options));
	}

	private Release doRollback(RollbackOptions options) {
		String name = options.getReleaseName();
		String namespace = options.getNamespace();
		int revision = options.getRevision();
		boolean noHooks = options.isNoHooks();
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
		// --dry-run: report the target revision without applying or storing anything.
		if (options.isDryRun()) {
			log.info("--dry-run: would roll back release {} to revision {}", name, revision);
			return newRelease;
		}
		List<HelmHook> hooks = noHooks ? List.of() : HookParser.parseHooks(manifest);
		HookExecutor hookExecutor = noHooks ? null : new HookExecutor(kubeService);
		if (!noHooks) {
			runHooks(hookExecutor, namespace, hooks, "pre-rollback");
		}
		applyRollback(options, newRelease, regularManifest);
		if (!noHooks) {
			runHooks(hookExecutor, namespace, hooks, "post-rollback");
		}
		if (options.isWait()) {
			kubeService.waitForReady(namespace, regularManifest, options.getTimeout(), options.isWaitForJobs());
		}
		return newRelease;
	}

	/**
	 * Applies the rolled-back manifest and stores the new revision, honoring the
	 * {@code --force}, {@code --cleanup-on-fail} and {@code --recreate-pods} options.
	 * @param options the rollback options
	 * @param newRelease the new revision to store after a successful apply
	 * @param regularManifest the hooks-stripped manifest to apply
	 */
	private void applyRollback(RollbackOptions options, Release newRelease, String regularManifest) {
		String name = newRelease.getName();
		String namespace = newRelease.getNamespace();
		if (options.isForce()) {
			log.info("--force: deleting existing resources of release {} before rollback", name);
			kubeService.delete(namespace, regularManifest);
		}
		try {
			kubeService.apply(namespace, regularManifest);
		}
		catch (RuntimeException ex) {
			if (options.isCleanupOnFail()) {
				cleanupFailedRollback(namespace, regularManifest, name);
			}
			throw ex;
		}
		kubeService.storeRelease(newRelease);
		kubeService.pruneReleaseHistory(name, namespace, options.getMaxHistory());
		if (options.isRecreatePods()) {
			log.info("--recreate-pods: restarting workloads of release {}", name);
			kubeService.restartWorkloads(namespace, regularManifest);
		}
	}

	// --cleanup-on-fail: best-effort deletion of resources created during a failed
	// rollback; a failed cleanup is logged and the original apply failure is rethrown.
	private void cleanupFailedRollback(String namespace, String regularManifest, String name) {
		log.warn("--cleanup-on-fail: deleting resources created during the failed rollback of {}", name);
		try {
			kubeService.delete(namespace, regularManifest);
		}
		catch (RuntimeException cleanupEx) {
			log.error("cleanup-on-fail delete failed for release {}: {}", name, cleanupEx.getMessage(), cleanupEx);
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
