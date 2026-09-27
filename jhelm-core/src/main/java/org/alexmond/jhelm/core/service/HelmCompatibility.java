package org.alexmond.jhelm.core.service;

import java.util.Locale;

/**
 * Which Helm major version jhelm renders like (#828).
 * <p>
 * The two lines differ in how {@code toYaml}/{@code toJson} treat nil values: Helm 3
 * emits {@code key: null}, Helm 4 omits the key. The mode also selects the default
 * {@code .Capabilities.HelmVersion} charts gate on, which {@code jhelm.helm-version} can
 * still override on its own.
 */
public enum HelmCompatibility {

	/** Helm 3 semantics: null-valued keys stay in {@code .Values}. */
	V3("v3.19.0"),

	/** Helm 4 semantics (default): null-valued keys are pruned from {@code .Values}. */
	V4("v4.3.0");

	private final String defaultHelmVersion;

	HelmCompatibility(String defaultHelmVersion) {
		this.defaultHelmVersion = defaultHelmVersion;
	}

	/**
	 * The Helm version reported as {@code .Capabilities.HelmVersion.Version} in this mode
	 * unless {@code jhelm.helm-version} overrides it.
	 * @return the version in {@code vX.Y.Z} form
	 */
	public String defaultHelmVersion() {
		return this.defaultHelmVersion;
	}

	/**
	 * Whether null-valued keys are pruned from the coalesced {@code .Values}, as Helm 4
	 * does.
	 * @return {@code true} for {@link #V4}
	 */
	public boolean prunesNullValues() {
		return this == V4;
	}

	/**
	 * Parses a configured mode, accepting {@code 3}, {@code v3}, {@code helm3} and the
	 * enum names, in any case.
	 * @param value the configured value, or {@code null}/blank for the default
	 * @return the mode, or {@link #V4} when unset
	 * @throws IllegalArgumentException if the value names no known Helm line
	 */
	public static HelmCompatibility from(String value) {
		if (value == null || value.isBlank()) {
			return V4;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT).replace("helm", "").replace("v", "").trim();
		return switch (normalized) {
			case "3" -> V3;
			case "4" -> V4;
			default ->
				throw new IllegalArgumentException("Unknown Helm compatibility '" + value + "': expected 3 or 4");
		};
	}

}
