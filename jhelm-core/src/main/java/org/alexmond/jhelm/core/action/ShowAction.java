package org.alexmond.jhelm.core.action;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;
import lombok.RequiredArgsConstructor;

import java.io.File;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.service.ChartLoader;

@RequiredArgsConstructor
public class ShowAction {

	private final ChartLoader chartLoader;

	public String showChart(String chartPath) throws Exception {
		Chart chart = chartLoader.load(new File(chartPath));
		return toYaml(chart.getMetadata());
	}

	public String showValues(String chartPath) throws Exception {
		Chart chart = chartLoader.load(new File(chartPath));
		if (chart.getValues() == null || chart.getValues().isEmpty()) {
			return "{}";
		}
		return toYaml(chart.getValues());
	}

	public String showReadme(String chartPath) throws Exception {
		Chart chart = chartLoader.load(new File(chartPath));
		return (chart.getReadme() != null) ? chart.getReadme() : "";
	}

	public String showCrds(String chartPath) throws Exception {
		Chart chart = chartLoader.load(new File(chartPath));
		if (chart.getCrds() == null || chart.getCrds().isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < chart.getCrds().size(); i++) {
			sb.append(chart.getCrds().get(i).getData());
			if (i < chart.getCrds().size() - 1) {
				sb.append("\n---\n");
			}
		}
		return sb.toString();
	}

	public String showAll(String chartPath) throws Exception {
		Chart chart = chartLoader.load(new File(chartPath));
		StringBuilder sb = new StringBuilder();

		sb.append("# Chart.yaml\n");
		sb.append(toYaml(chart.getMetadata()));

		sb.append("\n---\n# values.yaml\n");
		if (chart.getValues() != null && !chart.getValues().isEmpty()) {
			sb.append(toYaml(chart.getValues()));
		}
		else {
			sb.append("{}\n");
		}

		if (chart.getReadme() != null && !chart.getReadme().isEmpty()) {
			sb.append("\n---\n# README.md\n");
			sb.append(chart.getReadme());
		}

		if (chart.getCrds() != null && !chart.getCrds().isEmpty()) {
			sb.append("\n---\n# CRDs\n");
			for (int i = 0; i < chart.getCrds().size(); i++) {
				sb.append(chart.getCrds().get(i).getData());
				if (i < chart.getCrds().size() - 1) {
					sb.append("\n---\n");
				}
			}
		}

		return sb.toString();
	}

	private String toYaml(Object obj) throws Exception {
		YAMLMapper yamlMapper = YAMLMapper.builder()
			.disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
			.enable(YAMLWriteFeature.MINIMIZE_QUOTES)
			.changeDefaultPropertyInclusion((v) -> v.withValueInclusion(JsonInclude.Include.NON_NULL)
				.withContentInclusion(JsonInclude.Include.NON_NULL))
			.build();
		return yamlMapper.writeValueAsString(obj);
	}

}
