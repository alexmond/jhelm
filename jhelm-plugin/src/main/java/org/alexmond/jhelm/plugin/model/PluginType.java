package org.alexmond.jhelm.plugin.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PluginType {

	POST_RENDERER("postrenderer"),

	DOWNLOADER("downloader"),

	LIFECYCLE_HOOK("lifecyclehook");

	private final String value;

	PluginType(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return this.value;
	}

	public static PluginType fromValue(String value) {
		for (PluginType type : values()) {
			if (type.value.equalsIgnoreCase(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown plugin type: " + value);
	}

}
