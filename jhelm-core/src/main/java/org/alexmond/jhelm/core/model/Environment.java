package org.alexmond.jhelm.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mirror of the Spring Cloud Config Server structured Environment response ({@code GET
 * /{application}/{profiles}[/{label}]}).
 * <p>
 * {@link #propertySources} is ordered <em>highest precedence first</em>; each source's
 * {@code source} map is flat (dotted and {@code [i]}-indexed keys), not hierarchical. See
 * {@code PropertySourceMapper} for the un-flatten / strip / first-wins merge that turns
 * this into a nested values map.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Environment {

	private String name;

	private List<String> profiles = new ArrayList<>();

	private String label;

	private String version;

	private String state;

	private List<PropertySource> propertySources = new ArrayList<>();

	/**
	 * One document or file from the config server, with a flat (dotted/indexed) source
	 * map.
	 */
	@Getter
	@Setter
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class PropertySource {

		private String name;

		private Map<String, Object> source = new LinkedHashMap<>();

	}

}
