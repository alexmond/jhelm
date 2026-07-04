package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.service.ConfigServerValuesLoader;
import picocli.CommandLine.Option;

/**
 * Shared {@code --config-*} options for fetching chart values from a Spring Cloud Config
 * Server. Mixed into {@code template}/{@code install}/{@code upgrade}. Each option
 * overrides the corresponding {@code jhelm.config-server.*} property; an unset option
 * (including the {@code null} {@code --config-fail-fast}) defers to the property.
 */
public class ConfigServerCliOptions {

	@Option(names = { "--config-server-uri" },
			description = "Spring Cloud Config Server base URI; setting it enables the config-server source")
	private String uri;

	@Option(names = { "--config-name" }, description = "config-server application name (default: release name)")
	private String name;

	@Option(names = { "--config-label" }, description = "config-server label (e.g. a git branch)")
	private String label;

	@Option(names = { "--config-username" }, description = "config-server basic-auth username")
	private String username;

	@Option(names = { "--config-password" }, description = "config-server basic-auth password")
	private String password;

	@Option(names = { "--config-token" }, description = "config-server bearer token (takes precedence over basic auth)")
	private String token;

	@Option(names = { "--config-fail-fast" },
			description = "abort if the config server is unreachable (default: warn and continue)")
	private Boolean failFast;

	/**
	 * @return these CLI overrides as a loader options record
	 */
	public ConfigServerValuesLoader.Options toOptions() {
		return new ConfigServerValuesLoader.Options(uri, name, label, username, password, token, failFast);
	}

}
