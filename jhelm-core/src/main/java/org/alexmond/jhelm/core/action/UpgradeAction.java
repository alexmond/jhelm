package org.alexmond.jhelm.core.action;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.service.LifecycleListener;
import org.alexmond.jhelm.core.service.PostRenderProcessor;
import org.alexmond.jhelm.core.util.HookExecutor;
import org.alexmond.jhelm.core.util.HookParser;

@RequiredArgsConstructor
public class UpgradeAction {

	private final Engine engine;

	private final KubeService kubeService;

	@Setter
	private List<PostRenderProcessor> postRenderProcessors = List.of();

	@Setter
	private List<LifecycleListener> lifecycleListeners = List.of();

	public Release upgrade(Release currentRelease, Chart newChart, Map<String, Object> overrideValues, boolean dryRun)
			throws Exception {
		Map<String, Object> values = new HashMap<>(newChart.getValues());
		if (overrideValues != null) {
			values.putAll(overrideValues);
		}

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(currentRelease.getInfo().getFirstDeployed())
			.lastDeployed(OffsetDateTime.now())
			.status(dryRun ? "pending-upgrade" : "deployed")
			.description(dryRun ? "Dry run complete" : "Upgrade complete")
			.build();

		Release newRelease = Release.builder()
			.name(currentRelease.getName())
			.namespace(currentRelease.getNamespace())
			.version(currentRelease.getVersion() + 1)
			.chart(newChart)
			.info(info)
			.build();

		Map<String, Object> releaseData = new HashMap<>();
		releaseData.put("Name", newRelease.getName());
		releaseData.put("Namespace", newRelease.getNamespace());
		releaseData.put("Service", "Helm");
		releaseData.put("IsInstall", false);
		releaseData.put("IsUpgrade", true);

		String manifest = engine.render(newChart, values, releaseData);

		for (PostRenderProcessor processor : postRenderProcessors) {
			manifest = processor.process(manifest);
		}

		newRelease.setManifest(manifest);

		if (kubeService != null && !dryRun) {
			fireLifecycleEvent("pre-upgrade", newRelease.getName(), newRelease.getNamespace());
			List<HelmHook> hooks = HookParser.parseHooks(manifest);
			String regularManifest = HookParser.stripHooks(manifest);
			HookExecutor hookExecutor = new HookExecutor(kubeService);
			hookExecutor.run(newRelease.getNamespace(), hooks, "pre-upgrade", 300);
			kubeService.apply(newRelease.getNamespace(), regularManifest);
			kubeService.storeRelease(newRelease);
			hookExecutor.run(newRelease.getNamespace(), hooks, "post-upgrade", 300);
			fireLifecycleEvent("post-upgrade", newRelease.getName(), newRelease.getNamespace());
		}

		return newRelease;
	}

	private void fireLifecycleEvent(String phase, String releaseName, String namespace) throws Exception {
		for (LifecycleListener listener : lifecycleListeners) {
			listener.onEvent(phase, releaseName, namespace, Map.of());
		}
	}

}
