package org.alexmond.jhelm.core.action;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ReleaseContext;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.PostRenderProcessor;
import org.alexmond.jhelm.core.util.HookParser;
import org.alexmond.jhelm.core.util.ValuesLoader;

@RequiredArgsConstructor
public class TemplateAction {

	private final Engine engine;

	private final ChartLoader chartLoader;

	@Setter
	private List<PostRenderProcessor> postRenderProcessors = List.of();

	public String render(String chartPath, String releaseName, String namespace) {
		return render(chartPath, releaseName, namespace, new HashMap<>());
	}

	public String render(String chartPath, String releaseName, String namespace, Map<String, Object> overrides) {
		return render(chartPath, releaseName, namespace, overrides, null, List.of());
	}

	/**
	 * Renders a chart offline with an explicit {@code .Capabilities} override, mirroring
	 * {@code helm template --kube-version --api-versions}.
	 * @param chartPath path to the chart directory or archive
	 * @param releaseName the release name ({@code .Release.Name})
	 * @param namespace the release namespace
	 * @param overrides value overrides merged over the chart defaults
	 * @param kubeVersion the {@code .Capabilities.KubeVersion} override, or {@code null}
	 * for the engine default
	 * @param apiVersions extra API group/versions to advertise for
	 * {@code .Capabilities.APIVersions.Has} (additive to the default set)
	 * @return the rendered manifest
	 */
	public String render(String chartPath, String releaseName, String namespace, Map<String, Object> overrides,
			String kubeVersion, List<String> apiVersions) {
		Chart chart = this.chartLoader.load(new File(chartPath));

		Map<String, Object> values = new HashMap<>(chart.getValues());
		ValuesLoader.deepMerge(values, overrides);

		ReleaseContext releaseContext = ReleaseContext.builder()
			.name(releaseName)
			.namespace(namespace)
			.install(true)
			.upgrade(false)
			.revision(1)
			.build();

		String manifest = engine.render(chart, values, releaseContext, new Capabilities(kubeVersion, apiVersions));
		// helm template omits test hooks (they run only under `helm test`); lifecycle
		// hooks
		// are kept. See #634.
		manifest = HookParser.stripTestHooks(manifest);

		for (PostRenderProcessor processor : postRenderProcessors) {
			try {
				manifest = processor.process(manifest);
			}
			catch (Exception ex) {
				throw new JhelmException("Post-render processor failed", ex);
			}
		}

		return manifest;
	}

}
