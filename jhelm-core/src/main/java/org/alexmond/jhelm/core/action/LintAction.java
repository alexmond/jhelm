package org.alexmond.jhelm.core.action;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.SchemaValidationException;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.SchemaValidator;
import org.alexmond.jhelm.core.util.ValuesLoader;

@RequiredArgsConstructor
@Slf4j
public class LintAction {

	private final ChartLoader chartLoader;

	private final Engine engine;

	private final SchemaValidator schemaValidator;

	public LintResult lint(String chartPath, Map<String, Object> overrideValues, boolean strict) {
		List<String> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();

		File chartDir = new File(chartPath);
		if (!chartDir.exists() || !chartDir.isDirectory()) {
			errors.add("chart path \"" + chartPath + "\" does not exist or is not a directory");
			return new LintResult(chartPath, errors, warnings);
		}

		Chart chart;
		try {
			chart = chartLoader.load(chartDir);
		}
		catch (Exception ex) {
			errors.add("failed to load chart: " + ex.getMessage());
			return new LintResult(chartPath, errors, warnings);
		}

		validateMetadata(chart.getMetadata(), errors, warnings);
		validateValues(chart, overrideValues, errors);
		validateTemplates(chart, overrideValues, errors);

		return new LintResult(chartPath, errors, warnings);
	}

	private void validateMetadata(ChartMetadata metadata, List<String> errors, List<String> warnings) {
		if (metadata.getName() == null || metadata.getName().isBlank()) {
			errors.add("chart name is required in Chart.yaml");
		}
		if (metadata.getVersion() == null || metadata.getVersion().isBlank()) {
			errors.add("chart version is required in Chart.yaml");
		}
		if (metadata.getApiVersion() == null || metadata.getApiVersion().isBlank()) {
			errors.add("apiVersion is required in Chart.yaml");
		}
		else if (!"v2".equals(metadata.getApiVersion()) && !"v1".equals(metadata.getApiVersion())) {
			warnings.add("apiVersion '" + metadata.getApiVersion() + "' is not a known Helm API version");
		}
		if (metadata.getDescription() == null || metadata.getDescription().isBlank()) {
			warnings.add("chart is missing a description");
		}
		if (metadata.getType() != null && !"application".equals(metadata.getType())
				&& !"library".equals(metadata.getType())) {
			warnings.add("chart type '" + metadata.getType() + "' is not a recognized type (application or library)");
		}
	}

	private void validateValues(Chart chart, Map<String, Object> overrideValues, List<String> errors) {
		if (chart.getValuesSchema() == null || chart.getValuesSchema().isBlank()) {
			return;
		}
		Map<String, Object> values = new HashMap<>(chart.getValues());
		if (overrideValues != null) {
			ValuesLoader.deepMerge(values, overrideValues);
		}
		try {
			schemaValidator.validate(chart.getMetadata().getName(), chart.getValuesSchema(), values);
		}
		catch (SchemaValidationException ex) {
			for (String error : ex.getValidationErrors()) {
				errors.add("values schema validation: " + error);
			}
		}
	}

	private void validateTemplates(Chart chart, Map<String, Object> overrideValues, List<String> errors) {
		if ("library".equals(chart.getMetadata().getType())) {
			return;
		}
		Map<String, Object> values = new HashMap<>(chart.getValues());
		if (overrideValues != null) {
			ValuesLoader.deepMerge(values, overrideValues);
		}

		Map<String, Object> releaseData = new HashMap<>();
		releaseData.put("Name", "RELEASE-NAME");
		releaseData.put("Namespace", "default");
		releaseData.put("Service", "Helm");
		releaseData.put("IsInstall", true);
		releaseData.put("IsUpgrade", false);
		releaseData.put("Revision", 1);

		try {
			engine.render(chart, values, releaseData);
		}
		catch (Exception ex) {
			errors.add("template rendering failed: " + ex.getMessage());
		}
	}

	@lombok.Data
	@lombok.AllArgsConstructor
	public static class LintResult {

		private String chartPath;

		private List<String> errors;

		private List<String> warnings;

		public boolean isOk() {
			return errors.isEmpty();
		}

	}

}
