package org.alexmond.jhelm.core.action;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.PostRenderProcessor;
import org.alexmond.jhelm.core.util.ValuesLoader;

@RequiredArgsConstructor
public class TemplateAction {

	private final Engine engine;

	@Setter
	private List<PostRenderProcessor> postRenderProcessors = List.of();

	public String render(String chartPath, String releaseName, String namespace) throws Exception {
		return render(chartPath, releaseName, namespace, new HashMap<>());
	}

	public String render(String chartPath, String releaseName, String namespace, Map<String, Object> overrides)
			throws Exception {
		ChartLoader loader = new ChartLoader();
		Chart chart = loader.load(new File(chartPath));

		Map<String, Object> values = new HashMap<>(chart.getValues());
		ValuesLoader.deepMerge(values, overrides);

		Map<String, Object> releaseData = new HashMap<>();
		releaseData.put("Name", releaseName);
		releaseData.put("Namespace", namespace);
		releaseData.put("Service", "Helm");
		releaseData.put("IsInstall", true);
		releaseData.put("IsUpgrade", false);
		releaseData.put("Revision", 1);

		String manifest = engine.render(chart, values, releaseData);

		for (PostRenderProcessor processor : postRenderProcessors) {
			manifest = processor.process(manifest);
		}

		return manifest;
	}

}
