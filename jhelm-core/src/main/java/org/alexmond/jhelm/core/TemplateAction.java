package org.alexmond.jhelm.core;

import lombok.RequiredArgsConstructor;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class TemplateAction {

	private final Engine engine;

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

		return engine.render(chart, values, releaseData);
	}

}
