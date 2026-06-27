package org.alexmond.jhelm.core.model;

import java.time.OffsetDateTime;
import java.util.Map;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

import lombok.Builder;
import lombok.Value;

@JsonDeserialize(builder = Release.ReleaseBuilder.class)
@Builder(toBuilder = true)
@Value
public class Release {

	// Note: @Jacksonized would emit com.fasterxml (Jackson 2) builder annotations,
	// which are absent on this Jackson 3 (tools.jackson) classpath. The builder is
	// wired to Jackson 3 manually so the immutable type round-trips through the
	// Kubernetes release Secret.

	private String name;

	private String namespace;

	private int version;

	private Chart chart;

	private MapConfig config;

	private ReleaseInfo info;

	private String manifest;

	@JsonPOJOBuilder(withPrefix = "")
	public static class ReleaseBuilder {

	}

	@JsonDeserialize(builder = ReleaseInfo.ReleaseInfoBuilder.class)
	@Builder
	@Value
	public static class ReleaseInfo {

		private OffsetDateTime firstDeployed;

		private OffsetDateTime lastDeployed;

		private OffsetDateTime deleted;

		private String description;

		private ReleaseStatus status; // e.g., DEPLOYED, UNINSTALLED

		private String notes;

		@JsonPOJOBuilder(withPrefix = "")
		public static class ReleaseInfoBuilder {

		}

	}

	@JsonDeserialize(builder = MapConfig.MapConfigBuilder.class)
	@Builder
	@Value
	public static class MapConfig {

		private Map<String, Object> values;

		@JsonPOJOBuilder(withPrefix = "")
		public static class MapConfigBuilder {

		}

	}

}
