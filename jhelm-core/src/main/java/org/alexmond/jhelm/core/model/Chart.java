package org.alexmond.jhelm.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chart {

	private ChartMetadata metadata;

	/**
	 * Alias used to address this chart in parent values and templates. When a dependency
	 * declares an {@code alias}, the chart directory is named after the alias and parent
	 * values are looked up under that key instead of {@code metadata.name}.
	 */
	private String alias;

	@Builder.Default
	private List<Template> templates = new ArrayList<>();

	private Map<String, Object> values;

	/** Raw JSON content of {@code values.schema.json}, or {@code null} when absent. */
	private String valuesSchema;

	@Builder.Default
	private List<Chart> dependencies = new ArrayList<>();

	private String readme;

	@Builder.Default
	private List<Crd> crds = new ArrayList<>();

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Template {

		private String name;

		private String data;

	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Crd {

		private String name;

		private String data;

	}

}
