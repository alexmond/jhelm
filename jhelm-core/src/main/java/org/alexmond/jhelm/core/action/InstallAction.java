package org.alexmond.jhelm.core.action;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.DeploymentFailedException;
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
@Slf4j
public class InstallAction {

	private final Engine engine;

	private final KubeService kubeService;

	@Setter
	private List<PostRenderProcessor> postRenderProcessors = List.of();

	@Setter
	private List<LifecycleListener> lifecycleListeners = List.of();

	public Release install(Chart chart, String releaseName, String namespace, Map<String, Object> overrideValues,
			int version, boolean dryRun) throws Exception {
		if ("library".equals(chart.getMetadata().getType())) {
			throw new IllegalArgumentException(
					"chart '" + chart.getMetadata().getName() + "' is a library chart and cannot be installed");
		}
		Map<String, Object> values = new HashMap<>(chart.getValues());
		if (overrideValues != null) {
			values.putAll(overrideValues);
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
			manifest = processor.process(manifest);
		}

		release.setManifest(manifest);

		if (kubeService != null && !dryRun) {
			applyCrds(chart, namespace);
			fireLifecycleEvent("pre-install", releaseName, namespace);
			List<HelmHook> hooks = HookParser.parseHooks(manifest);
			String regularManifest = HookParser.stripHooks(manifest);
			HookExecutor hookExecutor = new HookExecutor(kubeService);
			hookExecutor.run(namespace, hooks, "pre-install", 300);
			kubeService.apply(namespace, regularManifest);
			try {
				kubeService.storeRelease(release);
			}
			catch (Exception ex) {
				rollbackAppliedResources(namespace, regularManifest);
				throw new DeploymentFailedException("Failed to store release after apply; resources rolled back", ex,
						regularManifest);
			}
			hookExecutor.run(namespace, hooks, "post-install", 300);
			fireLifecycleEvent("post-install", releaseName, namespace);
		}

		return release;
	}

	private void applyCrds(Chart chart, String namespace) throws Exception {
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

	private void fireLifecycleEvent(String phase, String releaseName, String namespace) throws Exception {
		for (LifecycleListener listener : lifecycleListeners) {
			listener.onEvent(phase, releaseName, namespace, Map.of());
		}
	}

}
