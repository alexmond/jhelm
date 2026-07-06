package org.alexmond.jhelm.core.config;

import java.util.ArrayList;
import java.util.List;

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

	/** Value-profile settings (Spring-Boot-style value profiles). */
	private final Profiles profiles = new Profiles();

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
	 * Path to the repository index cache directory. Defaults to
	 * {@code $HELM_REPOSITORY_CACHE} or the per-OS Helm cache location when not set.
	 */
	private String repositoryCachePath;

	/**
	 * Whether to skip TLS certificate verification for HTTP chart downloads. Defaults to
	 * {@code false}.
	 */
	private boolean insecureSkipTlsVerify;

	/**
	 * Whether to cache parsed template ASTs. Defaults to {@code true}.
	 */
	private boolean templateCacheEnabled = true;

	/**
	 * Maximum number of parsed templates in the LRU cache. Defaults to 256.
	 */
	private int templateCacheMaxSize = 256;

	/**
	 * Value-profile settings. Profiles gate {@code spring.config.activate.on-profile}
	 * documents and select {@code values-<profile>.yaml} sidecar files.
	 */
	@Getter
	@Setter
	public static class Profiles {

		/**
		 * Active value profiles, applied to chart {@code values.yaml}, {@code -f} files
		 * and their {@code -<profile>} sidecars. Also settable via the
		 * {@code JHELM_PROFILES_ACTIVE} environment variable, or per-command with
		 * {@code --profile} (which takes precedence).
		 */
		private List<String> active = new ArrayList<>();

	}

}
