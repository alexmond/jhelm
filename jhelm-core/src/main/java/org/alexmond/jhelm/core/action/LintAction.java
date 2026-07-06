package org.alexmond.jhelm.core.action;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.SchemaValidationException;
import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.ReleaseContext;
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
		return lint(chartPath, overrideValues, strict, false, null);
	}

	/**
	 * Lints a chart, optionally descending into its subcharts and rendering templates
	 * against a specific Kubernetes version.
	 * @param chartPath the chart directory to lint
	 * @param overrideValues user value overrides applied before schema/template checks
	 * @param strict whether warnings are treated as failures (evaluated by the caller)
	 * @param withSubcharts also lint each of the chart's subcharts
	 * ({@code --with-subcharts})
	 * @param kubeVersion the Kubernetes version to expose as
	 * {@code .Capabilities.KubeVersion} during template rendering, or {@code null} for
	 * the engine default ({@code --kube-version})
	 * @return the collected errors and warnings
	 */
	public LintResult lint(String chartPath, Map<String, Object> overrideValues, boolean strict, boolean withSubcharts,
			String kubeVersion) {
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
		validateTemplates(chart, overrideValues, errors, kubeVersion);

		if (withSubcharts) {
			for (Chart sub : chart.getDependencies()) {
				lintSubchart(sub, errors, warnings, kubeVersion);
			}
		}

		return new LintResult(chartPath, errors, warnings);
	}

	// Lints a loaded subchart, prefixing its findings with the subchart name so they are
	// attributable in the parent chart's combined report.
	private void lintSubchart(Chart sub, List<String> errors, List<String> warnings, String kubeVersion) {
		String name = (sub.getMetadata() != null && sub.getMetadata().getName() != null) ? sub.getMetadata().getName()
				: "subchart";
		List<String> subErrors = new ArrayList<>();
		List<String> subWarnings = new ArrayList<>();
		validateMetadata(sub.getMetadata(), subErrors, subWarnings);
		validateValues(sub, null, subErrors);
		validateTemplates(sub, null, subErrors, kubeVersion);
		for (String w : subWarnings) {
			warnings.add("[" + name + "] " + w);
		}
		for (String e : subErrors) {
			errors.add("[" + name + "] " + e);
		}
		for (Chart nested : sub.getDependencies()) {
			lintSubchart(nested, errors, warnings, kubeVersion);
		}
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

	private void validateTemplates(Chart chart, Map<String, Object> overrideValues, List<String> errors,
			String kubeVersion) {
		if ("library".equals(chart.getMetadata().getType())) {
			return;
		}
		Map<String, Object> values = new HashMap<>(chart.getValues());
		if (overrideValues != null) {
			ValuesLoader.deepMerge(values, overrideValues);
		}

		ReleaseContext releaseContext = ReleaseContext.builder()
			.name("RELEASE-NAME")
			.namespace("default")
			.install(true)
			.upgrade(false)
			.revision(1)
			.build();

		try {
			if (kubeVersion != null && !kubeVersion.isBlank()) {
				engine.render(chart, values, releaseContext, new Capabilities(kubeVersion, List.of()));
			}
			else {
				engine.render(chart, values, releaseContext);
			}
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
