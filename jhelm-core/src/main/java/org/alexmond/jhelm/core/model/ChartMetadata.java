package org.alexmond.jhelm.core.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartMetadata {

	private String name;

	private String version;

	private String description;

	private String apiVersion; // e.g., "v2"

	private String type; // e.g., "application"

	private String appVersion;

	private List<Dependency> dependencies;

	private Map<String, String> annotations;

}
