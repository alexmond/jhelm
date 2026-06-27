package org.alexmond.jhelm.mcp.config;

import lombok.Getter;
import lombok.Setter;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the jhelm MCP (Model Context Protocol) server module.
 *
 * <p>
 * The {@code jhelm.mcp.mode} property mirrors {@code jhelm.rest.mode}: it controls which
 * jhelm operations are exposed as MCP tools. In {@link JhelmAccessMode#READ_ONLY
 * READ_ONLY} mode (the default) only non-mutating tools (template, show, lint, search,
 * and the cluster-read release tools: list, status, history, get values/manifest) are
 * registered. Setting the property to the upper-case value {@code FULL}
 * ({@link JhelmAccessMode#FULL}) additionally registers the cluster-mutating release
 * tools (install, upgrade, uninstall, rollback, test).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jhelm.mcp")
public class JhelmMcpProperties {

	/**
	 * Access mode for the MCP server. Defaults to {@link JhelmAccessMode#READ_ONLY
	 * READ_ONLY}, which exposes only the non-mutating jhelm tools.
	 */
	private JhelmAccessMode mode = JhelmAccessMode.READ_ONLY;

}
