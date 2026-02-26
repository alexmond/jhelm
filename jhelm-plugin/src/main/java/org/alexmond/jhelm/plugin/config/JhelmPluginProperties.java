package org.alexmond.jhelm.plugin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the jhelm plugin system.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jhelm.plugins")
public class JhelmPluginProperties {

	/**
	 * Whether the plugin system is enabled.
	 */
	private boolean enabled;

	/**
	 * Directory where plugins are installed.
	 */
	private String directory;

	/**
	 * Default execution timeout in seconds.
	 */
	private int defaultTimeoutSeconds = 30;

	/**
	 * Default maximum WASM memory pages (64KB each).
	 */
	private int defaultMemoryLimitPages = 256;

}
