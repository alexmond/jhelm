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

/**
 * Implements {@code helm install}: renders a chart to a manifest, runs post-render
 * processors and lifecycle hooks, applies the resulting resources (and any CRDs) to the
 * cluster, and stores the resulting {@link Release}. On a failure after resources have
 * been applied it rolls them back and raises a {@link DeploymentFailedException}.
 */
@RequiredArgsConstructor
@Slf4j
public class InstallAction {

	private final Engine engine;

	private final KubeService kubeService;

	@Setter
	private List<PostRenderProcessor> postRenderProcessors = List.of();

	@Setter
	private List<LifecycleListener> lifecycleListeners = List.of();

	/**
	 * Installs a chart as a new release. Chart default values are merged with the
	 * supplied overrides, the templates are rendered, and—unless this is a dry run—the
	 * resulting resources, CRDs and hooks are applied to the cluster and the release is
	 * persisted.
	 * @param chart the chart to install (must not be a library chart)
	 * @param releaseName the name to give the release
	 * @param namespace the target namespace
	 * @param overrideValues user-supplied values merged over the chart defaults, may be
	 * {@code null}
	 * @param version the release revision number to assign
	 * @param dryRun if {@code true}, render only and skip applying anything to the
	 * cluster
	 * @return the resulting release, including the rendered manifest
	 * @throws IllegalArgumentException if the chart is a library chart
	 * @throws DeploymentFailedException if applied resources cannot be persisted (they
	 * are rolled back)
	 * @throws JhelmException if rendering or a cluster operation fails
	 */
	public Release install(Chart chart, String releaseName, String namespace, Map<String, Object> overrideValues,
			int version, boolean dryRun) {
		if ("library".equals(chart.getMetadata().getType())) {
			throw new IllegalArgumentException(
					"chart '" + chart.getMetadata().getName() + "' is a library chart and cannot be installed");
		}
		Map<String, Object> values = new HashMap<>(chart.getValues());
		if (overrideValues != null) {
			ValuesLoader.deepMerge(values, overrideValues);
		}

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now())
			.lastDeployed(OffsetDateTime.now())
			.status(dryRun ? "pending-install" : "deployed")
			.description(dryRun ? "Dry run complete" : "Install complete")
			.build();

		Release release = Release.builder()
			.name(releaseName)
			.namespace(namespace)
			.version(version)
			.chart(chart)
			.info(info)
			// Persist the user-supplied values (Helm's release "config"), so that
			// `get values` reports them and a later upgrade can reuse them.
			.config(Release.MapConfig.builder()
				.values((overrideValues != null) ? new HashMap<>(overrideValues) : new HashMap<>())
				.build())
			.build();

		Map<String, Object> releaseData = new HashMap<>();
		releaseData.put("Name", releaseName);
		releaseData.put("Namespace", namespace);
		releaseData.put("Service", "Helm");
		releaseData.put("IsInstall", true);
		releaseData.put("IsUpgrade", false);
		releaseData.put("Revision", release.getVersion());

		String manifest = engine.render(chart, values, releaseData);

		for (PostRenderProcessor processor : postRenderProcessors) {
			try {
				manifest = processor.process(manifest);
			}
			catch (Exception ex) {
				throw new JhelmException("Post-render processor failed", ex);
			}
		}

		release.setManifest(manifest);

		if (kubeService != null && !dryRun) {
			applyCrds(chart, namespace);
			fireLifecycleEvent("pre-install", releaseName, namespace);
			List<HelmHook> hooks = HookParser.parseHooks(manifest);
			String regularManifest = HookParser.stripHooks(manifest);
			HookExecutor hookExecutor = new HookExecutor(kubeService);
			runHooks(hookExecutor, namespace, hooks, "pre-install");
			kubeService.apply(namespace, regularManifest);
			try {
				kubeService.storeRelease(release);
			}
			catch (Exception ex) {
				rollbackAppliedResources(namespace, regularManifest);
				throw new DeploymentFailedException("Failed to store release after apply; resources rolled back", ex,
						regularManifest);
			}
			runHooks(hookExecutor, namespace, hooks, "post-install");
			fireLifecycleEvent("post-install", releaseName, namespace);
		}

		return release;
	}

	private void runHooks(HookExecutor hookExecutor, String namespace, List<HelmHook> hooks, String phase) {
		try {
			hookExecutor.run(namespace, hooks, phase, 300);
		}
		catch (Exception ex) {
			throw new JhelmException("Failed to run " + phase + " hooks", ex);
		}
	}

	private void applyCrds(Chart chart, String namespace) {
		if (chart.getCrds() == null || chart.getCrds().isEmpty()) {
			return;
		}
		for (Chart.Crd crd : chart.getCrds()) {
			if (log.isInfoEnabled()) {
				log.info("Installing CRD: {}", crd.getName());
			}
			kubeService.apply(namespace, crd.getData());
		}
	}

	private void rollbackAppliedResources(String namespace, String manifest) {
		try {
			if (log.isWarnEnabled()) {
				log.warn("Rolling back applied resources in namespace {}", namespace);
			}
			kubeService.delete(namespace, manifest);
		}
		catch (Exception rollbackEx) {
			if (log.isErrorEnabled()) {
				log.error("Failed to rollback applied resources: {}", rollbackEx.getMessage(), rollbackEx);
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
