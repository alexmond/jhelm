package org.alexmond.jhelm.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * In-memory representation of a loaded Helm chart: its {@link ChartMetadata}, default
 * values, templates, CRDs, subchart dependencies and any non-template files exposed via
 * {@code .Files}. Produced by the chart loader and consumed by the rendering engine.
 *
 * <p>
 * <strong>Mutability contract (1.0):</strong> this is a mutable internal model. The chart
 * loader and rendering engine mutate it during loading and rendering — the loader
 * resolves subchart aliases from the on-disk layout, and the engine marks
 * {@code .Chart.IsRoot} and applies dependency aliases while walking the render tree.
 * Treat an instance obtained from the API as read-only in your own code: build one with
 * the generated {@code builder()} and do not call the {@code set*} methods, whose
 * presence is an implementation detail of loading/rendering rather than part of the
 * supported surface. (Contrast {@link Release}, which is fully immutable.)
 */
@Data
@Builder(toBuilder = true)
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

	/** Non-template files from the chart archive (relative path to content). */
	@Builder.Default
	private Map<String, String> files = new LinkedHashMap<>();

	/**
	 * A single template file in the chart, identified by its path relative to the chart
	 * root (e.g. {@code templates/deployment.yaml}) and its raw text content.
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Template {

		private String name;

		private String data;

	}

	/**
	 * A CustomResourceDefinition file from the chart's {@code crds/} directory,
	 * identified by its relative path and raw YAML content. CRDs are installed before
	 * other resources and are not templated.
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Crd {

		private String name;

		private String data;

	}

}
