package org.alexmond.jhelm.mcp.tools;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * Read-only MCP tools for inspecting and rendering Helm charts. Each annotated method is
 * discovered by Spring AI's MCP annotation scanner and exposed as an MCP tool.
 */
@RequiredArgsConstructor
public class ChartTools {

	private final TemplateAction templateAction;

	private final ShowAction showAction;

	private final LintAction lintAction;

	/**
	 * Renders the templates of a chart locally, like {@code helm template}.
	 * @param chartPath path to the chart directory or packaged archive
	 * @param releaseName name to use for the rendered release
	 * @param namespace target Kubernetes namespace
	 * @return the rendered Kubernetes manifests as YAML
	 */
	@McpTool(name = "helm_template",
			description = "Render a Helm chart's templates locally (like 'helm template') and return the "
					+ "generated Kubernetes manifests as YAML. Read-only; does not touch any cluster.")
	public String template(
			@McpToolParam(description = "Path to the chart directory or packaged .tgz archive") String chartPath,
			@McpToolParam(description = "Release name to use while rendering") String releaseName,
			@McpToolParam(description = "Target Kubernetes namespace") String namespace) {
		return this.templateAction.render(chartPath, releaseName, namespace);
	}

	/**
	 * Shows a chart's {@code Chart.yaml} metadata, like {@code helm show chart}.
	 * @param chartPath path to the chart directory or packaged archive
	 * @return the chart metadata as YAML
	 */
	@McpTool(name = "helm_show_chart",
			description = "Show a chart's Chart.yaml metadata (like 'helm show chart'). Read-only.")
	public String showChart(
			@McpToolParam(description = "Path to the chart directory or packaged .tgz archive") String chartPath) {
		return this.showAction.showChart(chartPath);
	}

	/**
	 * Shows a chart's default values, like {@code helm show values}.
	 * @param chartPath path to the chart directory or packaged archive
	 * @return the default {@code values.yaml} content
	 */
	@McpTool(name = "helm_show_values",
			description = "Show a chart's default values.yaml (like 'helm show values'). Read-only.")
	public String showValues(
			@McpToolParam(description = "Path to the chart directory or packaged .tgz archive") String chartPath) {
		return this.showAction.showValues(chartPath);
	}

	/**
	 * Shows a chart's README, like {@code helm show readme}.
	 * @param chartPath path to the chart directory or packaged archive
	 * @return the chart README content, or an empty string if none is present
	 */
	@McpTool(name = "helm_show_readme", description = "Show a chart's README (like 'helm show readme'). Read-only.")
	public String showReadme(
			@McpToolParam(description = "Path to the chart directory or packaged .tgz archive") String chartPath) {
		return this.showAction.showReadme(chartPath);
	}

	/**
	 * Lints a chart for problems, like {@code helm lint}.
	 * @param chartPath path to the chart directory or packaged archive
	 * @param strict whether to treat warnings as failures
	 * @return a human-readable summary of lint errors and warnings
	 */
	@McpTool(name = "helm_lint",
			description = "Lint a Helm chart for problems (like 'helm lint') and return errors and warnings. "
					+ "Read-only.")
	public String lint(
			@McpToolParam(description = "Path to the chart directory or packaged .tgz archive") String chartPath,
			@McpToolParam(required = false, description = "Treat warnings as failures when true") boolean strict) {
		LintAction.LintResult result = this.lintAction.lint(chartPath, Map.of(), strict);
		StringBuilder sb = new StringBuilder();
		sb.append("Chart: ").append(result.getChartPath());
		sb.append("\nStatus: ").append(result.isOk() ? "OK" : "FAILED").append('\n');
		appendList(sb, "Errors", result.getErrors());
		appendList(sb, "Warnings", result.getWarnings());
		return sb.toString();
	}

	private static void appendList(StringBuilder sb, String label, List<String> messages) {
		sb.append(label).append(" (").append(messages.size()).append("):\n");
		for (String message : messages) {
			sb.append("  - ").append(message).append('\n');
		}
	}

}
