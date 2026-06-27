package org.alexmond.jhelm.mcp;

import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.action.SearchHubAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.mcp.config.JhelmMcpProperties;
import org.alexmond.jhelm.mcp.tools.ChartTools;
import org.alexmond.jhelm.mcp.tools.HubTools;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * lint, search) are exposed. Cluster-mutating operations (install, upgrade, uninstall,
 * rollback, test) are a {@code FULL}-mode follow-up and are not registered here.
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

}
