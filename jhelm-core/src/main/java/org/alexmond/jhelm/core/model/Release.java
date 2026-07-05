package org.alexmond.jhelm.core.model;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;

import lombok.Builder;
import lombok.Value;

/**
 * A Helm release record. Serializes to Helm's on-cluster release JSON schema (the payload
 * stored inside the {@code sh.helm.release.v1.*} Secret) so that {@code helm} and
 * {@code jhelm} can read each other's releases: nulls are omitted ({@code omitempty}),
 * unknown fields Helm writes but jhelm does not model (e.g. {@code hooks},
 * {@code labels}) are tolerated on read, and field names match Helm's wire format.
 */
@JsonDeserialize(builder = Release.ReleaseBuilder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
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

	/**
	 * Custom labels on the release, stored on the {@code sh.helm.release.v1.*} Secret
	 * (Helm's {@code --labels}). Omitted from the wire format when {@code null}.
	 */
	private Map<String, String> labels;

	@JsonPOJOBuilder(withPrefix = "")
	public static class ReleaseBuilder {

	}

	@JsonDeserialize(builder = ReleaseInfo.ReleaseInfoBuilder.class)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	@Builder
	@Value
	public static class ReleaseInfo {

		@JsonProperty("first_deployed")
		private OffsetDateTime firstDeployed;

		@JsonProperty("last_deployed")
		private OffsetDateTime lastDeployed;

		private OffsetDateTime deleted;

		private String description;

		private ReleaseStatus status; // e.g., DEPLOYED, UNINSTALLED

		private String notes;

		@JsonPOJOBuilder(withPrefix = "")
		public static class ReleaseInfoBuilder {

		}

	}

	@Builder
	@Value
	public static class MapConfig {

		private Map<String, Object> values;

		/**
		 * Serializes as Helm's bare {@code config} map — the user-supplied values
		 * directly, not wrapped in a {@code values} object.
		 * @return the values map
		 */
		@JsonValue
		public Map<String, Object> jsonValue() {
			return this.values;
		}

		/**
		 * Deserializes from Helm's bare {@code config} map.
		 * @param values the values map from the release payload
		 * @return the wrapped config
		 */
		@JsonCreator
		static MapConfig fromJson(Map<String, Object> values) {
			return MapConfig.builder().values(values).build();
		}

	}

}
