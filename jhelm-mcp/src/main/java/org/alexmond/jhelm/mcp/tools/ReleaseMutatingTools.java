package org.alexmond.jhelm.mcp.tools;

import java.io.File;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.RollbackOptions;
import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
import org.alexmond.jhelm.core.service.CascadePolicy;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.action.UpgradeOptions;
import org.alexmond.jhelm.core.action.UpgradeValueStrategy;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * Cluster-mutating MCP tools for the Helm release lifecycle (install, upgrade, uninstall,
 * rollback, test). Each annotated method is discovered by Spring AI's MCP annotation
 * scanner and exposed as an MCP tool.
 *
 * <p>
 * These tools are only registered when, under deny-by-default semantics,
 * {@code jhelm.security.mode=FULL} <em>and</em> a non-blank
 * {@code jhelm.security.api-key} is set. Otherwise they are not registered and therefore
 * do not appear in the MCP tool list at all — the MCP equivalent of the REST module's
 * mutating-operation block.
 *
 * <p>
 * Value overrides are not yet supported by these tools: installs and upgrades use the
 * chart's default values. This is a deliberate v1 simplification noted in each tool's
 * description.
 */
@RequiredArgsConstructor
public class ReleaseMutatingTools {

	private final InstallAction installAction;

	private final UpgradeAction upgradeAction;

	private final UninstallAction uninstallAction;

	private final RollbackAction rollbackAction;

	private final TestAction testAction;

	private final GetAction getAction;

	private final ChartLoader chartLoader;

	/**
	 * Installs a chart as a new release, like {@code helm install}. MUTATES the cluster
	 * unless {@code dryRun} is set.
	 * @param chartPath path to the chart directory to install
	 * @param name the release name
	 * @param namespace the target namespace
	 * @param dryRun {@code true} to render only without applying to the cluster
	 * @return a human-readable summary of the resulting release
	 */
	@McpTool(name = "helm_install",
			description = "Install a Helm chart as a new release (like 'helm install'). MUTATES the cluster: "
					+ "applies the rendered resources unless dryRun is true. Uses the chart's default values (value "
					+ "overrides are not yet supported). Set dryRun=true to preview without changing the cluster.")
	public String install(@McpToolParam(description = "Path to the chart directory to install") String chartPath,
			@McpToolParam(description = "Release name to create") String name,
			@McpToolParam(description = "Target Kubernetes namespace") String namespace,
			@McpToolParam(description = "Render only without applying to the cluster when true") boolean dryRun,
			@McpToolParam(required = false, description = "Custom release description") String description,
			@McpToolParam(required = false,
					description = "Custom labels to store on the release (key=value)") Map<String, String> labels) {
		Chart chart = this.chartLoader.load(new File(chartPath));
		Release release = this.installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName(name)
			.namespace(namespace)
			.values(Map.of())
			.revision(1)
			.dryRun(dryRun)
			.description(description)
			.labels((labels != null) ? labels : Map.of())
			.build());
		return renderReleaseSummary("Installed", release, dryRun);
	}

	/**
	 * Upgrades an existing release to a new chart, like {@code helm upgrade}. MUTATES the
	 * cluster unless {@code dryRun} is set.
	 * @param chartPath path to the new chart directory
	 * @param name the release name to upgrade
	 * @param namespace the Kubernetes namespace
	 * @param dryRun {@code true} to render only without applying to the cluster
	 * @return a human-readable summary of the upgraded release
	 */
	@McpTool(name = "helm_upgrade",
			description = "Upgrade an existing Helm release to a new chart (like 'helm upgrade'). MUTATES the "
					+ "cluster: applies the rendered resources unless dryRun is true. Uses the chart's default values "
					+ "with the DEFAULT value strategy (value overrides are not yet supported). Set dryRun=true to "
					+ "preview without changing the cluster.")
	public String upgrade(@McpToolParam(description = "Path to the new chart directory") String chartPath,
			@McpToolParam(description = "Release name to upgrade") String name,
			@McpToolParam(description = "Kubernetes namespace") String namespace,
			@McpToolParam(description = "Render only without applying to the cluster when true") boolean dryRun) {
		Release current = this.getAction.getRelease(name, namespace)
			.orElseThrow(() -> new IllegalArgumentException("Release '" + name + "' not found"));
		Chart chart = this.chartLoader.load(new File(chartPath));
		Release upgraded = this.upgradeAction.upgrade(UpgradeOptions.builder()
			.currentRelease(current)
			.newChart(chart)
			.values(Map.of())
			.valueStrategy(UpgradeValueStrategy.DEFAULT)
			.dryRun(dryRun)
			.build());
		return renderReleaseSummary("Upgraded", upgraded, dryRun);
	}

	/**
	 * Uninstalls a release, like {@code helm uninstall}. MUTATES the cluster.
	 * @param name the release name to uninstall
	 * @param namespace the Kubernetes namespace
	 * @return a confirmation message
	 */
	@McpTool(name = "helm_uninstall",
			description = "Uninstall a Helm release (like 'helm uninstall'). MUTATES the cluster: deletes the "
					+ "release's resources and removes its history.")
	public String uninstall(@McpToolParam(description = "Release name to uninstall") String name,
			@McpToolParam(description = "Kubernetes namespace") String namespace,
			@McpToolParam(required = false, description = "Simulate without deleting anything") boolean dryRun,
			@McpToolParam(required = false,
					description = "Wait until the resources are removed from the cluster") boolean wait,
			@McpToolParam(required = false, description = "Timeout in seconds for wait (default 300)") Integer timeout,
			@McpToolParam(required = false,
					description = "Deletion propagation: background, foreground, or orphan") String cascade,
			@McpToolParam(required = false,
					description = "Retain history and mark the release uninstalled") boolean keepHistory) {
		this.uninstallAction.uninstall(UninstallOptions.builder()
			.releaseName(name)
			.namespace(namespace)
			.dryRun(dryRun)
			.wait(wait)
			.timeout((timeout != null) ? timeout : 300)
			.cascade(CascadePolicy.fromString(cascade))
			.keepHistory(keepHistory)
			.build());
		String verb = dryRun ? "Would uninstall" : "Uninstalled";
		return verb + " release '" + name + "' from namespace '" + namespace + '\'';
	}

	/**
	 * Rolls a release back to a previous revision, like {@code helm rollback}. MUTATES
	 * the cluster.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @param revision the target revision number to roll back to
	 * @return a confirmation message
	 */
	@McpTool(name = "helm_rollback",
			description = "Roll a Helm release back to a previous revision (like 'helm rollback'). MUTATES the "
					+ "cluster: re-applies the target revision's resources as a new revision.")
	public String rollback(@McpToolParam(description = "Release name") String name,
			@McpToolParam(description = "Kubernetes namespace") String namespace,
			@McpToolParam(description = "Target revision number to roll back to") int revision,
			@McpToolParam(required = false,
					description = "Simulate without applying or storing a revision") boolean dryRun,
			@McpToolParam(required = false,
					description = "Delete and recreate resources instead of patching in place") boolean force,
			@McpToolParam(required = false,
					description = "Delete resources created during the rollback if it fails") boolean cleanupOnFail,
			@McpToolParam(required = false,
					description = "Rolling-restart the release's workloads after the rollback") boolean recreatePods,
			@McpToolParam(required = false,
					description = "Wait until the rolled-back resources are ready") boolean wait,
			@McpToolParam(required = false,
					description = "With wait, also wait for Jobs to complete") boolean waitForJobs,
			@McpToolParam(required = false,
					description = "Timeout in seconds for wait (default 300)") Integer timeout) {
		this.rollbackAction.rollback(RollbackOptions.builder()
			.releaseName(name)
			.namespace(namespace)
			.revision(revision)
			.dryRun(dryRun)
			.force(force)
			.cleanupOnFail(cleanupOnFail)
			.recreatePods(recreatePods)
			.wait(wait)
			.waitForJobs(waitForJobs)
			.timeout((timeout != null) ? timeout : 300)
			.build());
		String verb = dryRun ? "Would roll back" : "Rolled back";
		return verb + " release '" + name + "' in namespace '" + namespace + "' to revision " + revision;
	}

	/**
	 * Runs the test hooks of a release, like {@code helm test}. MUTATES the cluster.
	 * @param name the release name
	 * @param namespace the Kubernetes namespace
	 * @param timeoutSeconds the per-test timeout in seconds
	 * @return a human-readable summary of the test results
	 */
	@McpTool(name = "helm_test",
			description = "Run the test hooks of a Helm release (like 'helm test'). MUTATES the cluster: applies "
					+ "the test hook resources and waits for them to complete.")
	public String test(@McpToolParam(description = "Release name") String name,
			@McpToolParam(description = "Kubernetes namespace") String namespace,
			@McpToolParam(description = "Per-test timeout in seconds") int timeoutSeconds) {
		List<TestAction.TestResult> results = this.testAction.test(name, namespace, timeoutSeconds);
		if (results.isEmpty()) {
			return "No test hooks found for release '" + name + "' in namespace '" + namespace + '\'';
		}
		StringBuilder sb = new StringBuilder();
		for (TestAction.TestResult result : results) {
			sb.append(result.getStatus()).append('\t').append(result.getKind()).append('/').append(result.getName());
			if (result.getMessage() != null) {
				sb.append('\t').append(result.getMessage());
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	private static String renderReleaseSummary(String verb, Release release, boolean dryRun) {
		StringBuilder sb = new StringBuilder();
		sb.append(verb);
		if (dryRun) {
			sb.append(" (dry run)");
		}
		sb.append(" release '").append(release.getName());
		sb.append("' in namespace '").append(release.getNamespace());
		sb.append("' at revision ").append(release.getVersion());
		if (release.getInfo() != null && release.getInfo().getStatus() != null) {
			sb.append(" (status: ").append(release.getInfo().getStatus().getValue()).append(')');
		}
		return sb.toString();
	}

}
