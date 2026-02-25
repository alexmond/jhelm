package org.alexmond.jhelm.core.action;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.util.HookExecutor;
import org.alexmond.jhelm.core.util.HookParser;

@RequiredArgsConstructor
public class UninstallAction {

	private final KubeService kubeService;

	public void uninstall(String releaseName, String namespace) throws Exception {
		Optional<Release> releaseOpt = kubeService.getRelease(releaseName, namespace);
		if (releaseOpt.isEmpty()) {
			throw new RuntimeException("Release not found: " + releaseName);
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
