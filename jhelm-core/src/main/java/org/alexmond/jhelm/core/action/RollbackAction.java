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
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.util.HookExecutor;
import org.alexmond.jhelm.core.util.HookParser;

@RequiredArgsConstructor
@Slf4j
public class RollbackAction {

	private final KubeService kubeService;

	/**
	 * Rolls a release back to a previous revision, running its pre-rollback and
	 * post-rollback hooks.
	 * @param name the release name
	 * @param namespace the namespace the release lives in
	 * @param revision the revision number to roll back to
	 */
	public void rollback(String name, String namespace, int revision) {
		rollback(name, namespace, revision, false);
	}

	/**
	 * Rolls a release back to a previous revision, optionally skipping lifecycle hooks.
	 * @param name the release name
	 * @param namespace the namespace the release lives in
	 * @param revision the revision number to roll back to
	 * @param noHooks if {@code true}, skip running pre-rollback and post-rollback hooks
	 */
	public void rollback(String name, String namespace, int revision, boolean noHooks) {
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
				.status("deployed")
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
