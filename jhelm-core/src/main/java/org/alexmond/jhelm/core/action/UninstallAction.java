package org.alexmond.jhelm.core.action;

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
public class UninstallAction {

	private final KubeService kubeService;

	public void uninstall(String releaseName, String namespace) throws Exception {
		Optional<Release> releaseOpt = kubeService.getRelease(releaseName, namespace);
		if (releaseOpt.isEmpty()) {
			throw ReleaseNotFoundException.forRelease(releaseName, namespace);
		}

		Release release = releaseOpt.get();
		List<HelmHook> hooks = HookParser.parseHooks(release.getManifest());
		String regularManifest = HookParser.stripHooks(release.getManifest());
		HookExecutor hookExecutor = new HookExecutor(kubeService);
		hookExecutor.run(namespace, hooks, "pre-delete", 300);
		kubeService.delete(namespace, regularManifest);
		hookExecutor.run(namespace, hooks, "post-delete", 300);
		kubeService.deleteReleaseHistory(releaseName, namespace);
	}

}
