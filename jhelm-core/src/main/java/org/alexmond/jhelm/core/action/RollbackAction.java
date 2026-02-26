package org.alexmond.jhelm.core.action;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

	public void rollback(String name, String namespace, int revision) throws Exception {
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
		List<HelmHook> hooks = HookParser.parseHooks(manifest);
		String regularManifest = HookParser.stripHooks(manifest);
		HookExecutor hookExecutor = new HookExecutor(kubeService);
		hookExecutor.run(namespace, hooks, "pre-rollback", 300);
		kubeService.apply(namespace, regularManifest);
		kubeService.storeRelease(newRelease);
		hookExecutor.run(namespace, hooks, "post-rollback", 300);
	}

}
