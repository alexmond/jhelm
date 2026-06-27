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
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.service.LifecycleListener;
import org.alexmond.jhelm.core.service.LifecyclePhase;
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

	/**
	 * Upgrades a release, resolving values according to the configured
	 * {@link UpgradeValueStrategy}, optionally skipping hooks and pruning old revision
	 * history. After a successful (non-dry-run) store, prunes the release's revision
	 * history to the newest {@code maxHistory} revisions.
	 * @param options the upgrade options (current release, new chart, value overrides,
	 * value strategy, dry-run and no-hooks flags, and the history cap)
	 * @return the upgraded release
	 */
	public Release upgrade(UpgradeOptions options) {
		Chart newChart = options.getNewChart();
		if ("library".equals(newChart.getMetadata().getType())) {
			throw new IllegalArgumentException(
					"chart '" + newChart.getMetadata().getName() + "' is a library chart and cannot be upgraded");
		}

		Release currentRelease = options.getCurrentRelease();
		ResolvedValues resolved = resolveValues(currentRelease, newChart, options.getValues(),
				options.getValueStrategy());
		Map<String, Object> renderValues = resolved.render();
		Map<String, Object> configValues = resolved.config();

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(currentRelease.getInfo().getFirstDeployed())
			.lastDeployed(OffsetDateTime.now())
			.status((options.isDryRun()) ? ReleaseStatus.PENDING_UPGRADE : ReleaseStatus.DEPLOYED)
			.description(options.isDryRun() ? "Dry run complete" : "Upgrade complete")
			.build();

		Release newRelease = Release.builder()
			.name(currentRelease.getName())
			.namespace(currentRelease.getNamespace())
			.version(currentRelease.getVersion() + 1)
			.chart(newChart)
			.info(info)
			// Persist the resolved user-supplied values (Helm's release "config"), so
			// that
			// `get values` reports them and a later upgrade can reuse them.
			.config(Release.MapConfig.builder().values(configValues).build())
			.build();

		Map<String, Object> releaseData = new HashMap<>();
		releaseData.put("Name", newRelease.getName());
		releaseData.put("Namespace", newRelease.getNamespace());
		releaseData.put("Service", "Helm");
		releaseData.put("IsInstall", false);
		releaseData.put("IsUpgrade", true);
		releaseData.put("Revision", newRelease.getVersion());

		String manifest = engine.render(newChart, renderValues, releaseData);

		for (PostRenderProcessor processor : postRenderProcessors) {
			try {
				manifest = processor.process(manifest);
			}
			catch (Exception ex) {
				throw new JhelmException("Post-render processor failed", ex);
			}
		}

		newRelease = newRelease.toBuilder().manifest(manifest).build();

		if (kubeService != null && !options.isDryRun()) {
			boolean noHooks = options.isNoHooks();
			fireLifecycleEvent(LifecyclePhase.PRE_UPGRADE, newRelease.getName(), newRelease.getNamespace());
			String regularManifest = HookParser.stripHooks(manifest);
			List<HelmHook> hooks = noHooks ? List.of() : HookParser.parseHooks(manifest);
			HookExecutor hookExecutor = noHooks ? null : new HookExecutor(kubeService);
			if (!noHooks) {
				runHooks(hookExecutor, newRelease.getNamespace(), hooks, "pre-upgrade");
			}
			kubeService.apply(newRelease.getNamespace(), regularManifest);
			try {
				kubeService.storeRelease(newRelease);
			}
			catch (Exception ex) {
				reapplyPreviousRelease(currentRelease);
				throw new DeploymentFailedException("Failed to store release after apply; previous release re-applied",
						ex, regularManifest);
			}
			kubeService.pruneReleaseHistory(newRelease.getName(), newRelease.getNamespace(), options.getMaxHistory());
			if (!noHooks) {
				runHooks(hookExecutor, newRelease.getNamespace(), hooks, "post-upgrade");
			}
			fireLifecycleEvent(LifecyclePhase.POST_UPGRADE, newRelease.getName(), newRelease.getNamespace());
		}

		return newRelease;
	}

	/**
	 * Resolves the render and config value maps for an upgrade per the strategy matrix.
	 * <p>
	 * Let {@code over} = this command's overrides, {@code prior} = the previous release's
	 * persisted user values, {@code oldDefaults} = the previous chart's defaults and
	 * {@code newDefaults} = the new chart's defaults (all null-guarded to empty, all
	 * copied so inputs are never mutated):
	 * <ul>
	 * <li><b>RESET</b>: config = over; render = newDefaults + over (prior ignored)</li>
	 * <li><b>DEFAULT</b> (no over): config = prior; render = newDefaults + prior</li>
	 * <li><b>DEFAULT</b> (with over): config = over; render = newDefaults + over</li>
	 * <li><b>RESET_THEN_REUSE</b>: merged = prior + over; config = merged; render =
	 * newDefaults + merged</li>
	 * <li><b>REUSE</b>: merged = prior + over; config = merged; render = oldDefaults +
	 * prior + over</li>
	 * </ul>
	 * REUSE renders against the OLD defaults so new default changes are ignored, whereas
	 * RESET_THEN_REUSE renders against the NEW defaults.
	 */
	private ResolvedValues resolveValues(Release currentRelease, Chart newChart, Map<String, Object> overrideValues,
			UpgradeValueStrategy strategy) {
		Map<String, Object> over = (overrideValues != null) ? overrideValues : Map.of();
		Map<String, Object> prior = (currentRelease.getConfig() != null
				&& currentRelease.getConfig().getValues() != null) ? currentRelease.getConfig().getValues() : Map.of();
		Map<String, Object> oldDefaults = (currentRelease.getChart() != null
				&& currentRelease.getChart().getValues() != null) ? currentRelease.getChart().getValues() : Map.of();
		Map<String, Object> newDefaults = (newChart.getValues() != null) ? newChart.getValues() : Map.of();

		switch (strategy) {
			case RESET -> {
				Map<String, Object> render = new HashMap<>(newDefaults);
				ValuesLoader.deepMerge(render, over);
				return new ResolvedValues(render, new HashMap<>(over));
			}
			case RESET_THEN_REUSE -> {
				Map<String, Object> merged = new HashMap<>(prior);
				ValuesLoader.deepMerge(merged, over);
				Map<String, Object> render = new HashMap<>(newDefaults);
				ValuesLoader.deepMerge(render, merged);
				return new ResolvedValues(render, merged);
			}
			case REUSE -> {
				Map<String, Object> merged = new HashMap<>(prior);
				ValuesLoader.deepMerge(merged, over);
				Map<String, Object> render = new HashMap<>(oldDefaults);
				ValuesLoader.deepMerge(render, prior);
				ValuesLoader.deepMerge(render, over);
				return new ResolvedValues(render, merged);
			}
			default -> {
				Map<String, Object> userLayer = over.isEmpty() ? prior : over;
				Map<String, Object> render = new HashMap<>(newDefaults);
				ValuesLoader.deepMerge(render, userLayer);
				return new ResolvedValues(render, new HashMap<>(userLayer));
			}
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

	private void fireLifecycleEvent(LifecyclePhase phase, String releaseName, String namespace) {
		for (LifecycleListener listener : lifecycleListeners) {
			try {
				listener.onEvent(phase, releaseName, namespace, Map.of());
			}
			catch (Exception ex) {
				throw new JhelmException("Lifecycle listener failed for phase " + phase.getValue(), ex);
			}
		}
	}

	/**
	 * The two value maps resolved for an upgrade: {@code render} is passed to the engine,
	 * {@code config} is persisted as the release's user-value layer.
	 */
	private record ResolvedValues(Map<String, Object> render, Map<String, Object> config) {
	}

}
