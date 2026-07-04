package org.alexmond.jhelm.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for fetching chart values from a Spring Cloud Config Server. Field names
 * mirror the Spring Cloud Config client ({@code spring.cloud.config.*}). Disabled by
 * default, so binding these properties without setting {@link #enabled} + {@link #uri} is
 * a no-op.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jhelm.config-server")
public class ConfigServerProperties {

	/** Whether to fetch values from a config server. Defaults to {@code false}. */
	private boolean enabled;

	/** Base URI of the config server, e.g. {@code https://config.example.com}. */
	private String uri;

	/**
	 * Config-server application name (the {@code {application}} path segment). Defaults
	 * to the release name when unset.
	 */
	private String name;

	/**
	 * Config-server label (the optional {@code {label}} path segment, e.g. a git branch).
	 * Omitted from the URL when unset, letting the server use its default.
	 */
	private String label;

	/** Basic-auth username. */
	private String username;

	/** Basic-auth password. */
	private String password;

	/** Bearer token (mutually exclusive with basic auth; used when set). */
	private String token;

	/**
	 * When {@code true}, a config-server fetch failure aborts the operation; otherwise a
	 * warning is logged and rendering continues with local values only. Defaults to
	 * {@code false}, matching Spring's {@code spring.cloud.config.fail-fast}.
	 */
	private boolean failFast;

	/**
	 * When {@code true}, config-server values drop to the <em>lowest</em> precedence —
	 * every local {@code -f} file and {@code --set} wins over them. Mirrors
	 * {@code spring.cloud.config.override-none}. Defaults to {@code false}.
	 */
	private boolean overrideNone;

	/**
	 * When {@code true}, config-server values move <em>above</em> the {@code --set}
	 * family, so the remote values win over command-line overrides. Mirrors
	 * {@code spring.cloud.config.override-system-properties}. Defaults to {@code false}.
	 */
	private boolean overrideSystemProperties;

}
