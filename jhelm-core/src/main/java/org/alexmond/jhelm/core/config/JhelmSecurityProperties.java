package org.alexmond.jhelm.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Unified security configuration shared by every jhelm adapter (REST, MCP, ...).
 *
 * <p>
 * This replaces the per-adapter {@code jhelm.rest.mode} and {@code jhelm.mcp.mode}
 * properties with a single {@code jhelm.security.*} namespace, so an operator configures
 * the security posture once and every surface agrees on it.
 *
 * <p>
 * <strong>Deny-by-default:</strong> the mode defaults to {@link JhelmAccessMode#READ_ONLY
 * READ_ONLY} and there is no default API key, so a freshly started jhelm adapter never
 * exposes cluster-mutating operations until an operator both opts into
 * {@link JhelmAccessMode#FULL FULL} and configures an API key. The effective policy is
 * derived by {@link JhelmSecurityPolicy}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jhelm.security")
public class JhelmSecurityProperties {

	/**
	 * Unified access mode for all jhelm adapters. Defaults to
	 * {@link JhelmAccessMode#READ_ONLY READ_ONLY} (read-only by default):
	 * cluster-mutating operations stay disabled until this is set to
	 * {@link JhelmAccessMode#FULL FULL} <em>and</em> an {@link #apiKey} is configured.
	 */
	private JhelmAccessMode mode = JhelmAccessMode.READ_ONLY;

	/**
	 * The API key that protects cluster-mutating operations. When {@code null} or blank,
	 * no key is configured and mutating operations remain disabled regardless of
	 * {@link #mode} (deny-by-default). Callers present this value in the
	 * {@link #apiKeyHeader} request header.
	 */
	private String apiKey;

	/**
	 * Name of the request header carrying the API key. Defaults to {@code X-API-Key}.
	 */
	private String apiKeyHeader = "X-API-Key";

}
