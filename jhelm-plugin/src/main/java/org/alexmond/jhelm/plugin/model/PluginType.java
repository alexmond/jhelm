package org.alexmond.jhelm.plugin.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumerates the kinds of plugin the jhelm plugin system supports. The wire value of each
 * constant matches the {@code type} field of a {@code plugin.yaml} manifest.
 */
public enum PluginType {

	/**
	 * Post-renderer plugin that transforms manifests after rendering, before they are
	 * applied to the cluster.
	 */
	POST_RENDERER("postrenderer"),

	/**
	 * Downloader plugin that fetches charts from a custom protocol or location.
	 */
	DOWNLOADER("downloader"),

	/**
	 * Lifecycle-hook plugin that reacts to release lifecycle events.
	 */
	LIFECYCLE_HOOK("lifecyclehook");

	private final String value;

	PluginType(String value) {
		this.value = value;
	}

	/**
	 * Return the manifest wire value for this plugin type.
	 * @return the lowercase string used in {@code plugin.yaml}
	 */
	@JsonValue
	public String getValue() {
		return this.value;
	}

	/**
	 * Resolve a {@link PluginType} from its manifest wire value.
	 * @param value the manifest type string (case-insensitive)
	 * @return the matching plugin type
	 * @throws IllegalArgumentException if no type matches the given value
	 */
	public static PluginType fromValue(String value) {
		for (PluginType type : values()) {
			if (type.value.equalsIgnoreCase(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown plugin type: " + value);
	}

}
