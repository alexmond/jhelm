package org.alexmond.jhelm.plugin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the {@code plugin.yaml} manifest inside a plugin archive.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginManifest {

	private String apiVersion;

	private String name;

	private String version;

	private PluginType type;

	private String description;

	private String runtime;

	private WasmConfig wasm;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WasmConfig {

		private String entrypoint;

		@Builder.Default
		@JsonProperty("memoryLimitPages")
		private int memoryLimitPages = 256;

		@Builder.Default
		@JsonProperty("timeoutSeconds")
		private int timeoutSeconds = 30;

	}

}
