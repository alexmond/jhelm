package org.alexmond.jhelm.mcp.tools;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.model.Release;
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
			description = "List the Helm releases deployed in a Kubernetes namespace (like 'helm list'), returning "
					+ "each release's name, namespace, revision and status. Read-only; does not modify the cluster.")
	public String list(@McpToolParam(description = "Kubernetes namespace to list releases in") String namespace) {
		List<Release> releases = this.listAction.list(namespace);
		if (releases.isEmpty()) {
			return "No releases found in namespace: " + namespace;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("NAME\tNAMESPACE\tREVISION\tSTATUS\n");
		for (Release release : releases) {
			appendReleaseLine(sb, release);
		}
		return sb.toString();
	}

	/**
	 * Reports the status of a single release, like {@code helm status}.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @return a human-readable status summary, or a not-found message
	 */
	@McpTool(name = "helm_status",
			description = "Get the status of a single Helm release (like 'helm status'), including its revision, "
					+ "deployment status and chart. Read-only; does not modify the cluster.")
	public String status(@McpToolParam(description = "Release name") String name,
			@McpToolParam(description = "Kubernetes namespace") String namespace) {
		Optional<Release> release = this.statusAction.status(name, namespace);
		if (release.isEmpty()) {
			return notFound(name, namespace);
		}
		return renderReleaseDetail(release.get());
	}

	/**
	 * Lists the revision history of a release, like {@code helm history}.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @return a human-readable, newline-separated summary of revisions
	 */
	@McpTool(name = "helm_history",
			description = "List the revision history of a Helm release (like 'helm history'), one line per "
					+ "revision. Read-only; does not modify the cluster.")
	public String history(@McpToolParam(description = "Release name") String name,
			@McpToolParam(description = "Kubernetes namespace") String namespace) {
		List<Release> revisions = this.historyAction.history(name, namespace);
		if (revisions.isEmpty()) {
			return notFound(name, namespace);
		}
		StringBuilder sb = new StringBuilder();
		sb.append("NAME\tNAMESPACE\tREVISION\tSTATUS\n");
		for (Release revision : revisions) {
			appendReleaseLine(sb, revision);
		}
		return sb.toString();
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

	private static void appendReleaseLine(StringBuilder sb, Release release) {
		sb.append(release.getName()).append('\t').append(release.getNamespace()).append('\t');
		sb.append(release.getVersion()).append('\t').append(statusOf(release)).append('\n');
	}

	private static String renderReleaseDetail(Release release) {
		StringBuilder sb = new StringBuilder();
		sb.append("Name: ").append(release.getName());
		sb.append("\nNamespace: ").append(release.getNamespace());
		sb.append("\nRevision: ").append(release.getVersion());
		sb.append("\nStatus: ").append(statusOf(release));
		Release.ReleaseInfo info = release.getInfo();
		if (info != null) {
			if (info.getDescription() != null) {
				sb.append("\nDescription: ").append(info.getDescription());
			}
			if (info.getLastDeployed() != null) {
				sb.append("\nLast deployed: ").append(info.getLastDeployed());
			}
		}
		if (release.getChart() != null && release.getChart().getMetadata() != null) {
			sb.append("\nChart: ")
				.append(release.getChart().getMetadata().getName())
				.append('-')
				.append(release.getChart().getMetadata().getVersion());
		}
		sb.append('\n');
		return sb.toString();
	}

	private static String statusOf(Release release) {
		if (release.getInfo() != null && release.getInfo().getStatus() != null) {
			return release.getInfo().getStatus().getValue();
		}
		return "unknown";
	}

	private static String notFound(String name, String namespace) {
		return "Release '" + name + "' not found in namespace '" + namespace + '\'';
	}

}
