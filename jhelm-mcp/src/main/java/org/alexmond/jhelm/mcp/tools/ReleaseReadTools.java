package org.alexmond.jhelm.mcp.tools;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.output.OutputFormat;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * Read-only MCP tools for inspecting Helm releases deployed to a Kubernetes cluster. Each
 * annotated method is discovered by Spring AI's MCP annotation scanner and exposed as an
 * MCP tool. None of these tools mutate the cluster; they are always registered regardless
 * of the configured access mode.
 */
@RequiredArgsConstructor
public class ReleaseReadTools {

	private final ListAction listAction;

	private final StatusAction statusAction;

	private final GetAction getAction;

	private final HistoryAction historyAction;

	/**
	 * Lists the Helm releases in a namespace, like {@code helm list}.
	 * @param namespace the Kubernetes namespace to list releases in
	 * @return a human-readable, newline-separated summary of releases
	 */
	@McpTool(name = "helm_list",
			description = "List the Helm releases deployed in a Kubernetes namespace (like 'helm list -o json'), "
					+ "returning a JSON array of releases (name, namespace, revision, updated, status, chart, "
					+ "app_version). Empty array when none. Read-only; does not modify the cluster.")
	public List<Map<String, Object>> list(
			@McpToolParam(description = "Kubernetes namespace to list releases in") String namespace) {
		return this.listAction.list(namespace).stream().map(OutputFormat::listRow).toList();
	}

	/**
	 * Reports the status of a single release, like {@code helm status}.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @return a human-readable status summary, or a not-found message
	 */
	@McpTool(name = "helm_status",
			description = "Get the status of a single Helm release (like 'helm status -o json') as a JSON object "
					+ "with nested 'info' (status, description, timestamps), version, namespace and manifest. Returns "
					+ "an object with an 'error' field when the release is not found. Read-only; does not modify the "
					+ "cluster.")
	public Map<String, Object> status(@McpToolParam(description = "Release name") String name,
			@McpToolParam(description = "Kubernetes namespace") String namespace) {
		return this.statusAction.status(name, namespace)
			.map(OutputFormat::release)
			.orElseGet(() -> Map.of("error", notFound(name, namespace)));
	}

	/**
	 * Lists the revision history of a release, like {@code helm history}.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @return a human-readable, newline-separated summary of revisions
	 */
	@McpTool(name = "helm_history",
			description = "List the revision history of a Helm release (like 'helm history -o json') as a JSON array, "
					+ "one object per revision (revision, updated, status, chart, app_version, description). Empty "
					+ "array when the release is not found. Read-only; does not modify the cluster.")
	public List<Map<String, Object>> history(@McpToolParam(description = "Release name") String name,
			@McpToolParam(description = "Kubernetes namespace") String namespace) {
		return this.historyAction.history(name, namespace).stream().map(OutputFormat::historyRow).toList();
	}

	/**
	 * Returns the values of a release as YAML, like {@code helm get values}.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @param all {@code true} to include computed chart defaults, {@code false} for only
	 * user-supplied overrides
	 * @return the values YAML, or a not-found message
	 */
	@McpTool(name = "helm_get_values",
			description = "Get the values used by a Helm release as YAML (like 'helm get values'). Set 'all' to "
					+ "include computed chart defaults. Read-only; does not modify the cluster.")
	public String getValues(@McpToolParam(description = "Release name") String name,
			@McpToolParam(description = "Kubernetes namespace") String namespace, @McpToolParam(required = false,
					description = "Include computed chart defaults when true; only user overrides when false") boolean all) {
		Optional<Release> release = this.getAction.getRelease(name, namespace);
		if (release.isEmpty()) {
			return notFound(name, namespace);
		}
		return this.getAction.getValues(release.get(), all);
	}

	/**
	 * Returns the rendered Kubernetes manifest of a release, like
	 * {@code helm get manifest}.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @return the manifest YAML, or a not-found message
	 */
	@McpTool(name = "helm_get_manifest",
			description = "Get the rendered Kubernetes manifest of a Helm release as YAML (like 'helm get "
					+ "manifest'). Read-only; does not modify the cluster.")
	public String getManifest(@McpToolParam(description = "Release name") String name,
			@McpToolParam(description = "Kubernetes namespace") String namespace) {
		Optional<Release> release = this.getAction.getRelease(name, namespace);
		if (release.isEmpty()) {
			return notFound(name, namespace);
		}
		return this.getAction.getManifest(release.get());
	}

	private static String notFound(String name, String namespace) {
		return "Release '" + name + "' not found in namespace '" + namespace + '\'';
	}

}
