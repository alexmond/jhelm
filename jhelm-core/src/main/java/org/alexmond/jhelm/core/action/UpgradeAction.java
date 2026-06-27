package org.alexmond.jhelm.core.action;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.DeploymentFailedException;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.service.LifecycleListener;
import org.alexmond.jhelm.core.service.PostRenderProcessor;
import org.alexmond.jhelm.core.util.HookExecutor;
import org.alexmond.jhelm.core.util.HookParser;
import org.alexmond.jhelm.core.util.ValuesLoader;

@RequiredArgsConstructor
@Slf4j
public class UpgradeAction {

	private final Engine engine;

	private final KubeService kubeService;

	@Setter
	private List<PostRenderProcessor> postRenderProcessors = List.of();

	@Setter
	private List<LifecycleListener> lifecycleListeners = List.of();

	public Release upgrade(Release currentRelease, Chart newChart, Map<String, Object> overrideValues, boolean dryRun) {
		if ("library".equals(newChart.getMetadata().getType())) {
			throw new IllegalArgumentException(
					"chart '" + newChart.getMetadata().getName() + "' is a library chart and cannot be upgraded");
		}
		Map<String, Object> values = new HashMap<>(newChart.getValues());
		if (overrideValues != null) {
			ValuesLoader.deepMerge(values, overrideValues);
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
			// Persist the user-supplied values (Helm's release "config"), so that
			// `get values` reports them and a later upgrade can reuse them.
			.config(Release.MapConfig.builder()
				.values((overrideValues != null) ? new HashMap<>(overrideValues) : new HashMap<>())
				.build())
			.build();

		Map<String, Object> releaseData = new HashMap<>();
		releaseData.put("Name", newRelease.getName());
		releaseData.put("Namespace", newRelease.getNamespace());
		releaseData.put("Service", "Helm");
		releaseData.put("IsInstall", false);
		releaseData.put("IsUpgrade", true);
		releaseData.put("Revision", newRelease.getVersion());

		String manifest = engine.render(newChart, values, releaseData);

		for (PostRenderProcessor processor : postRenderProcessors) {
			try {
				manifest = processor.process(manifest);
			}
			catch (Exception ex) {
				throw new JhelmException("Post-render processor failed", ex);
			}
		}

		newRelease.setManifest(manifest);

		if (kubeService != null && !dryRun) {
			fireLifecycleEvent("pre-upgrade", newRelease.getName(), newRelease.getNamespace());
			List<HelmHook> hooks = HookParser.parseHooks(manifest);
			String regularManifest = HookParser.stripHooks(manifest);
			HookExecutor hookExecutor = new HookExecutor(kubeService);
			runHooks(hookExecutor, newRelease.getNamespace(), hooks, "pre-upgrade");
			kubeService.apply(newRelease.getNamespace(), regularManifest);
			try {
				kubeService.storeRelease(newRelease);
			}
			catch (Exception ex) {
				reapplyPreviousRelease(currentRelease);
				throw new DeploymentFailedException("Failed to store release after apply; previous release re-applied",
						ex, regularManifest);
			}
			runHooks(hookExecutor, newRelease.getNamespace(), hooks, "post-upgrade");
			fireLifecycleEvent("post-upgrade", newRelease.getName(), newRelease.getNamespace());
		}

		return newRelease;
	}

	private void runHooks(HookExecutor hookExecutor, String namespace, List<HelmHook> hooks, String phase) {
		try {
			hookExecutor.run(namespace, hooks, phase, 300);
		}
		catch (Exception ex) {
			throw new JhelmException("Failed to run " + phase + " hooks", ex);
		}
	}

	private void reapplyPreviousRelease(Release previousRelease) {
		if (previousRelease.getManifest() == null) {
			return;
		}
		try {
			if (log.isWarnEnabled()) {
				log.warn("Re-applying previous release {} v{}", previousRelease.getName(),
						previousRelease.getVersion());
			}
			String regularManifest = HookParser.stripHooks(previousRelease.getManifest());
			kubeService.apply(previousRelease.getNamespace(), regularManifest);
		}
		catch (Exception rollbackEx) {
			if (log.isErrorEnabled()) {
				log.error("Failed to re-apply previous release: {}", rollbackEx.getMessage(), rollbackEx);
			}
		}
	}

	private void fireLifecycleEvent(String phase, String releaseName, String namespace) {
		for (LifecycleListener listener : lifecycleListeners) {
			try {
				listener.onEvent(phase, releaseName, namespace, Map.of());
			}
			catch (Exception ex) {
				throw new JhelmException("Lifecycle listener failed for phase " + phase, ex);
			}
		}
	}

}
