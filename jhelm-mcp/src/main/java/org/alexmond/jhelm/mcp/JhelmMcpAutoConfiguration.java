package org.alexmond.jhelm.mcp;

import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.SearchHubAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.mcp.config.JhelmMcpProperties;
import org.alexmond.jhelm.mcp.tools.ChartTools;
import org.alexmond.jhelm.mcp.tools.HubTools;
import org.alexmond.jhelm.mcp.tools.ReleaseMutatingTools;
import org.alexmond.jhelm.mcp.tools.ReleaseReadTools;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the jhelm MCP (Model Context Protocol) server module. Activates
 * only when the Spring AI MCP server annotation API is on the classpath. Runs after
 * {@link JhelmCoreAutoConfiguration} so that all core action beans are available for the
 * MCP tools.
 *
 * <p>
 * Each tool holder is registered as a Spring bean so that Spring AI's MCP annotation
 * scanner can discover the {@link McpTool @McpTool} methods (component scanning does not
 * run for a library on the classpath).
 *
 * <p>
 * <strong>Access mode:</strong> {@code jhelm.mcp.mode} mirrors {@code jhelm.rest.mode}.
 * In the default {@code READ_ONLY} mode only the non-mutating tools (template, show,
 * lint, search, plus the cluster-read release tools: list, status, history, get
 * values/manifest) are exposed. The cluster-mutating tools (install, upgrade, uninstall,
 * rollback, test) are registered only when {@code jhelm.mcp.mode} is set to the
 * upper-case value {@code FULL}; in {@code READ_ONLY} mode they are not registered and do
 * not appear in the MCP tool list at all.
 */
@AutoConfiguration(after = JhelmCoreAutoConfiguration.class)
@ConditionalOnClass(McpTool.class)
@EnableConfigurationProperties(JhelmMcpProperties.class)
public class JhelmMcpAutoConfiguration {

	/**
	 * Registers the read-only chart MCP tools when the required chart actions are
	 * present.
	 * @param templateAction renders chart templates
	 * @param showAction exposes chart metadata, values and README
	 * @param lintAction validates charts
	 * @return the chart tools bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ TemplateAction.class, ShowAction.class, LintAction.class })
	public ChartTools jhelmChartTools(TemplateAction templateAction, ShowAction showAction, LintAction lintAction) {
		return new ChartTools(templateAction, showAction, lintAction);
	}

	/**
	 * Registers the read-only Artifact Hub MCP tool when a {@link SearchHubAction} is
	 * present.
	 * @param searchHubAction performs Artifact Hub searches
	 * @return the hub tools bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(SearchHubAction.class)
	public HubTools jhelmHubTools(SearchHubAction searchHubAction) {
		return new HubTools(searchHubAction);
	}

	/**
	 * Registers the read-only release MCP tools when the required cluster-read actions
	 * are present. These tools are always registered regardless of the configured access
	 * mode.
	 * @param listAction lists releases in a namespace
	 * @param statusAction reports release status
	 * @param getAction reads release values and manifest
	 * @param historyAction lists release revisions
	 * @return the read-only release tools bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ ListAction.class, StatusAction.class, GetAction.class, HistoryAction.class })
	public ReleaseReadTools jhelmReleaseReadTools(ListAction listAction, StatusAction statusAction, GetAction getAction,
			HistoryAction historyAction) {
		return new ReleaseReadTools(listAction, statusAction, getAction, historyAction);
	}

	/**
	 * Registers the cluster-mutating release MCP tools only when
	 * {@code jhelm.mcp.mode=FULL} (upper-case) and the required mutating actions are
	 * present. In the default {@code READ_ONLY} mode this bean is not created, so the
	 * mutating tools never appear in the MCP tool list.
	 * @param installAction installs releases
	 * @param upgradeAction upgrades releases
	 * @param uninstallAction uninstalls releases
	 * @param rollbackAction rolls releases back to a previous revision
	 * @param testAction runs release test hooks
	 * @param getAction resolves the current release for upgrades
	 * @param chartLoader loads charts for install and upgrade
	 * @return the mutating release tools bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "jhelm.mcp.mode", havingValue = "FULL")
	@ConditionalOnBean({ InstallAction.class, UpgradeAction.class, UninstallAction.class, RollbackAction.class,
			TestAction.class })
	public ReleaseMutatingTools jhelmReleaseMutatingTools(InstallAction installAction, UpgradeAction upgradeAction,
			UninstallAction uninstallAction, RollbackAction rollbackAction, TestAction testAction, GetAction getAction,
			ChartLoader chartLoader) {
		return new ReleaseMutatingTools(installAction, upgradeAction, uninstallAction, rollbackAction, testAction,
				getAction, chartLoader);
	}

}
