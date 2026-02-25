package org.alexmond.jhelm.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the jhelm core module.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jhelm")
public class JhelmCoreProperties {

	/**
	 * Path to the Helm repository configuration file. Defaults to
	 * {@code ~/.config/helm/repositories.yaml} when not set.
	 */
	private String configPath;

	/**
	 * Path to the Helm OCI registry auth config file. Defaults to the platform-specific
	 * location when not set.
	 */
	private String registryConfigPath;

	/**
	 * Whether to skip TLS certificate verification for HTTP chart downloads. Defaults to
	 * {@code false}.
	 */
	private boolean insecureSkipTlsVerify = false;

}
