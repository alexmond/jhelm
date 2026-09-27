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
	 * Path to the Helm repository configuration file. Bound at the {@code jhelm} root as
	 * {@code jhelm.config-path}; {@code jhelm.core.config-path} is also accepted as a
	 * relaxed alias (resolved in the auto-configuration). When neither is set it defaults
	 * to the operator's real Helm location ({@code $HELM_REPOSITORY_CONFIG} or
	 * {@code ~/.config/helm/repositories.yaml}), which jhelm reads AND writes — embedders
	 * should set this explicitly.
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
	 * Helm version reported to charts as {@code .Capabilities.HelmVersion.Version}. This
	 * is the compatibility version charts gate on (e.g. {@code semverCompare ">=3.0.0"}),
	 * not jhelm's own version, which templates see as
	 * {@code .Capabilities.JhelmVersion.Version}. Defaults to the latest Helm release
	 * ({@code v4.3.0}).
	 */
	private String helmVersion;

	/**
	 * Which Helm major version jhelm renders like: {@code 4} (default) or {@code 3}. Helm
	 * 4 prunes null-valued keys from the coalesced {@code .Values}, where Helm 3 keeps
	 * them, and the mode also selects the default {@code .Capabilities.HelmVersion}.
	 */
	private String helmCompatibility;

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
