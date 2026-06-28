package org.alexmond.jhelm.core.action;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ReleaseContext;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.PostRenderProcessor;
import org.alexmond.jhelm.core.util.ValuesLoader;

@RequiredArgsConstructor
public class TemplateAction {

	private final Engine engine;

	@Setter
	private List<PostRenderProcessor> postRenderProcessors = List.of();

	public String render(String chartPath, String releaseName, String namespace) {
		return render(chartPath, releaseName, namespace, new HashMap<>());
	}

	public String render(String chartPath, String releaseName, String namespace, Map<String, Object> overrides) {
		ChartLoader loader = new ChartLoader();
		Chart chart = loader.load(new File(chartPath));

		Map<String, Object> values = new HashMap<>(chart.getValues());
		ValuesLoader.deepMerge(values, overrides);

		ReleaseContext releaseContext = ReleaseContext.builder()
			.name(releaseName)
			.namespace(namespace)
			.install(true)
			.upgrade(false)
			.revision(1)
			.build();

		String manifest = engine.render(chart, values, releaseContext);

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
