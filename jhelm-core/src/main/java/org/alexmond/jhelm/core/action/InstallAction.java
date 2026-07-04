package org.alexmond.jhelm.core.action;

import org.alexmond.jhelm.core.util.ReleaseNames;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.DeploymentFailedException;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseContext;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.service.LifecycleListener;
import org.alexmond.jhelm.core.service.ValueEncryptor;
import org.alexmond.jhelm.core.service.LifecyclePhase;
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

	@Setter
	private JhelmMetrics metrics;

	@Setter
	private ValueEncryptor valueEncryptor = new ValueEncryptor(null, null, true);

	/**
	 * Installs a chart as a new release. Chart default values are merged with the
	 * supplied overrides, the templates are rendered, and—unless this is a dry run—the
	 * resulting resources, CRDs and hooks are applied to the cluster and the release is
	 * persisted.
	 * @param options the install options (chart, release name, namespace, value
	 * overrides, revision, dry-run and no-hooks flags)
	 * @return the resulting release, including the rendered manifest
	 * @throws IllegalArgumentException if the chart is a library chart
	 * @throws DeploymentFailedException if applied resources cannot be persisted (they
	 * are rolled back)
	 * @throws JhelmException if rendering or a cluster operation fails
	 */
	public Release install(InstallOptions options) {
		return (this.metrics == null) ? doInstall(options)
				: this.metrics.timeAction("install", () -> doInstall(options));
	}

	private Release doInstall(InstallOptions options) {
		ReleaseNames.validateReleaseName(options.getReleaseName());
		ReleaseNames.validateNamespace(options.getNamespace());
		Chart chart = options.getChart();
		if ("library".equals(chart.getMetadata().getType())) {
			throw new IllegalArgumentException(
					"chart '" + chart.getMetadata().getName() + "' is a library chart and cannot be installed");
		}
		Map<String, Object> overrideValues = options.getValues();
		Map<String, Object> values = new HashMap<>(chart.getValues());
		if (overrideValues != null) {
			ValuesLoader.deepMerge(values, overrideValues);
		}
		this.valueEncryptor.decryptValues(values);

		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.firstDeployed(OffsetDateTime.now())
			.lastDeployed(OffsetDateTime.now())
			.status((options.isDryRun()) ? ReleaseStatus.PENDING_INSTALL : ReleaseStatus.DEPLOYED)
			.description(options.isDryRun() ? "Dry run complete" : "Install complete")
			.build();

		Release release = Release.builder()
			.name(options.getReleaseName())
			.namespace(options.getNamespace())
			.version(options.getRevision())
			.chart(chart)
			.info(info)
			// Persist the user-supplied values (Helm's release "config").
			.config(Release.MapConfig.builder()
				.values((overrideValues != null) ? new HashMap<>(overrideValues) : new HashMap<>())
				.build())
			.build();

		ReleaseContext releaseContext = ReleaseContext.builder()
			.name(options.getReleaseName())
			.namespace(options.getNamespace())
			.install(true)
			.upgrade(false)
			.revision(release.getVersion())
			.build();

		Capabilities fromCluster = (kubeService != null) ? kubeService.getCapabilities() : null;
		Capabilities capabilities = (fromCluster != null) ? fromCluster : Capabilities.DEFAULT;
		String manifest = runPostRenderProcessors(engine.render(chart, values, releaseContext, capabilities));

		release = release.toBuilder().manifest(manifest).build();

		if (kubeService != null && !options.isDryRun()) {
			applyRelease(options, chart, release, manifest);
		}
		else if (kubeService != null && options.isServerDryRun()) {
			// server-side dry-run: validate against the API server without persisting the
			// release or running hooks
			kubeService.applyDryRun(options.getNamespace(), HookParser.stripHooks(manifest));
		}

		return release;
	}

	/**
	 * Applies a non-dry-run install to the cluster: ensures the namespace, applies CRDs,
	 * runs pre-install hooks, applies the hook-stripped manifest, stores the release
	 * (rolling back applied resources on failure) and runs post-install hooks.
	 * @param options the install options
	 * @param chart the chart being installed (for CRDs)
	 * @param release the release being applied
	 * @param manifest the full rendered manifest (hooks not yet stripped)
	 */
	private void applyRelease(InstallOptions options, Chart chart, Release release, String manifest) {
		String namespace = options.getNamespace();
		boolean noHooks = options.isNoHooks();
		if (options.isCreateNamespace()) {
			kubeService.ensureNamespace(namespace);
		}
		applyCrds(chart, namespace);
		fireLifecycleEvent(LifecyclePhase.PRE_INSTALL, options.getReleaseName(), namespace);
		String regularManifest = HookParser.stripHooks(manifest);
		List<HelmHook> hooks = noHooks ? List.of() : HookParser.parseHooks(manifest);
		HookExecutor hookExecutor = noHooks ? null : new HookExecutor(kubeService);
		if (!noHooks) {
			runHooks(hookExecutor, namespace, hooks, "pre-install");
		}
		kubeService.apply(namespace, regularManifest);
		try {
			kubeService.storeRelease(release);
		}
		catch (Exception ex) {
			rollbackAppliedResources(namespace, regularManifest);
			throw new DeploymentFailedException("Failed to store release after apply; resources rolled back", ex,
					regularManifest);
		}
		if (!noHooks) {
			runHooks(hookExecutor, namespace, hooks, "post-install");
		}
		fireLifecycleEvent(LifecyclePhase.POST_INSTALL, options.getReleaseName(), namespace);
	}

	private String runPostRenderProcessors(String manifest) {
		String result = manifest;
		for (PostRenderProcessor processor : postRenderProcessors) {
			try {
				result = processor.process(result);
			}
			catch (Exception ex) {
				throw new JhelmException("Post-render processor failed", ex);
			}
		}
		return result;
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

}
