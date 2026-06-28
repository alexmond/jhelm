package org.alexmond.jhelm.mcp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the jhelm MCP (Model Context Protocol) server module.
 *
 * <p>
 * Security for the MCP server is governed by the unified {@code jhelm.security.*}
 * namespace (see {@link org.alexmond.jhelm.core.config.JhelmSecurityProperties}), not by
 * an MCP-specific mode. The cluster-mutating MCP tools are registered only when, under
 * deny-by-default semantics, {@code jhelm.security.mode=FULL} <em>and</em> a non-blank
 * {@code jhelm.security.api-key} is set.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jhelm.mcp")
public class JhelmMcpProperties {

}
