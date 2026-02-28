package org.alexmond.jhelm.core.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.cache.TemplateCache;
import org.alexmond.jhelm.core.exception.SchemaValidationException;
import org.alexmond.jhelm.core.exception.TemplateRenderException;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.internal.parse.Node;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.VersionSet;

@Slf4j
public class Engine {

	// Full set of API group versions matching k8s client-go v1.31 scheme registration.
	// Helm builds this from scheme.Scheme.PrioritizedVersionsAllGroups().
	private static final List<String> DEFAULT_API_VERSIONS = List.of("v1", "admissionregistration.k8s.io/v1",
			"admissionregistration.k8s.io/v1beta1", "apiextensions.k8s.io/v1", "apiregistration.k8s.io/v1", "apps/v1",
			"authentication.k8s.io/v1", "authentication.k8s.io/v1beta1", "authorization.k8s.io/v1", "autoscaling/v2",
			"autoscaling/v1", "batch/v1", "certificates.k8s.io/v1", "certificates.k8s.io/v1alpha1",
			"coordination.k8s.io/v1", "coordination.k8s.io/v1alpha2", "discovery.k8s.io/v1", "events.k8s.io/v1",
			"flowcontrol.apiserver.k8s.io/v1", "flowcontrol.apiserver.k8s.io/v1beta3",
			"internal.apiserver.k8s.io/v1alpha1", "networking.k8s.io/v1", "networking.k8s.io/v1alpha1",
			"node.k8s.io/v1", "policy/v1", "rbac.authorization.k8s.io/v1", "resource.k8s.io/v1alpha3",
			"scheduling.k8s.io/v1", "storage.k8s.io/v1", "storage.k8s.io/v1alpha1", "storagemigration.k8s.io/v1alpha1");

	private final Map<String, String> namedTemplates = new HashMap<>();

	private final TemplateCache templateCache;

	private final SchemaValidator schemaValidator;

	private final JhelmMetrics metrics;

	private GoTemplate factory;

	public Engine() {
		this(null, new SchemaValidator(), null);
	}

	public Engine(TemplateCache templateCache, SchemaValidator schemaValidator) {
		this(templateCache, schemaValidator, null);
	}

	public Engine(TemplateCache templateCache, SchemaValidator schemaValidator, JhelmMetrics metrics) {
		this.templateCache = templateCache;
		this.schemaValidator = (schemaValidator != null) ? schemaValidator : new SchemaValidator();
		this.metrics = metrics;
	}

	private void parseWithCache(String name, String text) throws Exception {
		if (templateCache == null) {
			factory.parse(name, text);
			return;
		}
		String cacheKey = name + "|" + text.hashCode();
		Map<String, Node> cached = templateCache.get(cacheKey);
		if (cached != null) {
			factory.getRootNodes().putAll(cached);
			return;
		}
		Map<String, Node> before = new LinkedHashMap<>(factory.getRootNodes());
		factory.parse(name, text);
		Map<String, Node> after = factory.getRootNodes();
		Map<String, Node> added = new LinkedHashMap<>();
		for (Map.Entry<String, Node> entry : after.entrySet()) {
			if (!before.containsKey(entry.getKey())) {
				added.put(entry.getKey(), entry.getValue());
			}
		}
		templateCache.put(cacheKey, added);
	}

	private boolean isTruthy(Object context) {
		if (context == null) {
			return false;
		}
		if (context instanceof Boolean b) {
			return b;
		}
		if (context instanceof String s) {
			return !s.isEmpty();
		}
		if (context instanceof Iterable i) {
			return i.iterator().hasNext();
		}
		if (context instanceof Map m) {
			return !m.isEmpty();
		}
		if (context instanceof Number n) {
			return n.doubleValue() != 0;
		}
		return true;
	}

	@SneakyThrows
	public String render(Chart chart, Map<String, Object> values, Map<String, Object> releaseInfo) {
		long startNanos = System.nanoTime();
		try {
			return doRender(chart, values, releaseInfo);
		}
		finally {
			if (metrics != null) {
				metrics.recordRender(System.nanoTime() - startNanos);
			}
		}
	}

	private String doRender(Chart chart, Map<String, Object> values, Map<String, Object> releaseInfo) {
		namedTemplates.clear();
		// Create a new template for each render to avoid accumulation
		this.factory = new GoTemplate();
		// Collect all named templates (define blocks) first
		collectNamedTemplates(chart);

		try {
			// Using a shared set for the whole rendering process to avoid redundant work
			// and loops
			Set<String> renderedCharts = new HashSet<>();
			String rendered = renderWithSubcharts(chart, values, releaseInfo, renderedCharts, 0);
			return cleanManifest(rendered);
		}
		catch (StackOverflowError ex) {
			String chartName = chart.getMetadata().getName();
			if (log.isErrorEnabled()) {
				log.error("StackOverflowError during rendering of chart '{}': recursive template inclusion detected",
						chartName);
			}
			return "ERROR: chart '" + chartName
					+ "': recursive template inclusion or too deep nesting. Check for circular {{ template }} calls.";
		}
	}

	/**
	 * Clean up the manifest by removing empty YAML documents and trailing separators
	 */
	private String cleanManifest(String manifest) {
		if (manifest == null || manifest.isEmpty()) {
			return manifest;
		}

		// Normalize the manifest first - ensure it doesn't start/end with ---
		String normalized = manifest.trim();

		// Fix any ---# patterns (separator immediately followed by comment)
		// This happens when templates use {{- if -}} around separators
		normalized = normalized.replace("---#", "---\n#");

		// Remove leading --- if present
		while (normalized.startsWith("---")) {
			normalized = normalized.substring(3).trim();
		}

		// Remove trailing --- if present
		while (normalized.endsWith("---")) {
			normalized = normalized.substring(0, normalized.length() - 3).trim();
		}

		// Split by YAML document separator patterns:
		// 1. \n---\n (standard separator with blank line)
		// 2. \n--- followed by newline with content (template-generated separator)
		// Use lookbehind to keep the newline before --- but not the --- itself
		String[] docs = normalized.split("\\n---(?=\\n)");
		StringBuilder cleaned = new StringBuilder();

		for (String doc : docs) {
			String trimmed = doc.trim();
			// Skip empty documents, standalone separators, and documents that are just
			// comments after a separator
			if (!trimmed.isEmpty() && !trimmed.equals("---") && !trimmed.startsWith("---")) {
				if (cleaned.length() > 0) {
					cleaned.append("\n---\n");
				}
				cleaned.append(trimmed);
			}
		}

		// Add final newline if there's content
		if (cleaned.length() > 0) {
			cleaned.append('\n');
		}

		return cleaned.toString();
	}

	private void collectNamedTemplates(Chart chart) {
		// First pass: collect all templates without parsing, to have them available for
		// definitions
		// Also track which chart each template belongs to for proper namespacing
		Map<String, String> allTemplates = new HashMap<>();
		Map<String, String> templateToChartName = new HashMap<>();
		collectAllTemplates(chart, allTemplates, templateToChartName, chart.getMetadata().getName());

		// Second pass: try to parse each template. Templates with definitions (tpl)
		// should be parsed first.
		List<String> sortedNames = allTemplates.keySet().stream().sorted((a, b) -> {
			boolean aTpl = a.endsWith(".tpl");
			boolean bTpl = b.endsWith(".tpl");
			if (aTpl && !bTpl) {
				return -1;
			}
			if (!aTpl && bTpl) {
				return 1;
			}
			return a.compareTo(b);
		}).toList();

		// Store original rootNodes count
		int beforeCount = factory.getRootNodes().size();

		for (String name : sortedNames) {
			try {
				String chartName = templateToChartName.get(name);
				parseWithCache(name, allTemplates.get(name));
				if (log.isDebugEnabled()) {
					log.debug("Parsed template: {} (from chart: {})", name, chartName);
				}

				// After parsing, check if new named templates (defines) were added
				// If this is a _helpers.tpl from a subchart, we need to alias the
				// templates
				if (name.contains("_helpers") || name.endsWith(".tpl")) {
					int afterCount = factory.getRootNodes().size();
					if (afterCount > beforeCount) {
						// New named templates were added
						// We need to create aliases with chart prefix
						createChartPrefixedAliases(chartName);
					}
					beforeCount = afterCount;
				}
			}
			catch (Exception ex) {
				if (log.isWarnEnabled()) {
					log.warn("Parse failure for template '{}' in chart '{}': {}", name, templateToChartName.get(name),
							ex.getMessage());
				}
			}
		}

		// Count how many named templates (defines) we actually collected
		int namedTemplateCount = factory.getRootNodes().size() - allTemplates.size();
		if (log.isDebugEnabled()) {
			log.debug("Collected {} named templates from {} files", namedTemplateCount, allTemplates.size());
		}
	}

	private void createChartPrefixedAliases(String chartName) {
		// Get the rootNodes map to find newly added templates
		Map<String, Node> rootNodes = factory.getRootNodes();
		List<String> allKeys = new ArrayList<>(rootNodes.keySet());

		// Templates added between beforeCount and afterCount are the new ones
		// We need to create aliases with chart name prefix
		for (String templateName : allKeys) {
			// Skip file templates (they usually have paths)
			if (templateName.contains("/") || templateName.contains("\\")) {
				continue;
			}

			// If this template doesn't already have the chart prefix, create an alias
			if (!templateName.startsWith(chartName + ".")) {
				String prefixedName = chartName + "." + templateName;
				// Add alias: (prefixedName) -> same node as templateName
				Node node = rootNodes.get(templateName);
				if (node != null && !rootNodes.containsKey(prefixedName)) {
					rootNodes.put(prefixedName, node);
					if (log.isDebugEnabled()) {
						log.debug("Created template alias: {} -> {}", prefixedName, templateName);
					}
				}
			}
		}
	}

	private void collectAllTemplates(Chart chart, Map<String, String> templates,
			Map<String, String> templateToChartName, String rootChartName) {
		String chartName = chart.getMetadata().getName();

		for (Chart.Template t : chart.getTemplates()) {
			// Use chart-prefixed key to avoid collisions between charts with same
			// template names
			String uniqueKey = chartName + ":" + t.getName();
			templates.put(uniqueKey, t.getData());
			templateToChartName.put(uniqueKey, chartName);
		}

		for (Chart subchart : chart.getDependencies()) {
			collectAllTemplates(subchart, templates, templateToChartName, rootChartName);
		}
	}

	private String renderWithSubcharts(Chart chart, Map<String, Object> values, Map<String, Object> releaseInfo,
			Set<String> renderedCharts, int depth) {
		String chartKey = chart.getMetadata().getName() + ":" + chart.getMetadata().getVersion();
		if (renderedCharts.contains(chartKey)) {
			if (log.isDebugEnabled()) {
				log.debug("Chart {} already rendered in this path, skipping to avoid recursion", chartKey);
			}
			return "";
		}
		if (depth > 3) { // Even further reduced depth
			if (log.isWarnEnabled()) {
				log.warn("Subchart nesting depth too high (>3) for chart {}", chartKey);
			}
			return "";
		}
		renderedCharts.add(chartKey);

		// Merge chart default values with provided values
		Map<String, Object> chartValues = (chart.getValues() != null) ? chart.getValues() : new HashMap<>();
		Map<String, Object> mergedValues = mergeValues(chartValues, values);

		// Helm makes subchart defaults available to parent templates via
		// .Values.<subchartName>.*
		mergeSubchartDefaults(chart, mergedValues);

		// Validate merged values against the chart's JSON Schema (if present)
		if (chart.getValuesSchema() != null) {
			try {
				schemaValidator.validate(chart.getMetadata().getName(), chart.getValuesSchema(), mergedValues);
			}
			catch (SchemaValidationException ex) {
				throw new TemplateRenderException("Values schema validation failed: " + ex.getMessage(), ex,
						chart.getMetadata().getName(), null);
			}
		}

		Map<String, Object> context = new HashMap<>();
		context.put("Values", mergedValues);
		context.put("Chart", chart.getMetadata());
		context.put("Release", releaseInfo);

		// Add standard Helm objects
		context.put("Capabilities",
				Map.of("KubeVersion",
						Map.of("Version", "v1.31.0", "Major", "1", "Minor", "31", "GitVersion", "v1.31.0"),
						"APIVersions", new VersionSet(DEFAULT_API_VERSIONS)));
		context.put("Template", Map.of("Name", "", "BasePath", "templates"));

		StringBuilder sb = new StringBuilder();

		// Render current chart templates
		renderChartTemplates(chart, context, sb);

		// Render subcharts
		for (Chart subchart : chart.getDependencies()) {
			// Use alias as lookup key when set (alias takes precedence over chart name)
			String subchartName = (subchart.getAlias() != null) ? subchart.getAlias()
					: subchart.getMetadata().getName();

			// Subcharts are only rendered if enabled in Values
			@SuppressWarnings("unchecked")
			Map<String, Object> subchartOverrides = (Map<String, Object>) mergedValues.getOrDefault(subchartName,
					new HashMap<>());

			if (log.isDebugEnabled()) {
				log.debug("Subchart {}: overrides={}, enabled={}", subchartName, subchartOverrides,
						subchartOverrides.get("enabled"));
			}

			// If subchart is disabled, skip it
			if (subchartOverrides.containsKey("enabled") && !isTruthy(subchartOverrides.get("enabled"))) {
				if (log.isDebugEnabled()) {
					log.debug("Subchart {} is disabled", subchartName);
				}
				continue;
			}

			// In Helm, global values are shared
			if (mergedValues.containsKey("global")) {
				subchartOverrides.put("global", mergedValues.get("global"));
			}

			if (log.isDebugEnabled()) {
				log.debug("Rendering subchart: {}", subchartName);
			}
			sb.append(renderWithSubcharts(subchart, subchartOverrides, releaseInfo, renderedCharts, depth + 1));
		}

		return sb.toString();
	}

	private void renderChartTemplates(Chart chart, Map<String, Object> context, StringBuilder sb) {
		for (Chart.Template t : chart.getTemplates()) {
			if (!t.getName().endsWith(".yaml")) {
				continue;
			}
			try {
				parseWithCache(t.getName(), t.getData());
				StringWriter writer = new StringWriter();

				@SuppressWarnings("unchecked")
				Map<String, Object> templateMap = new HashMap<>((Map<String, Object>) context.get("Template"));
				templateMap.put("Name", chart.getMetadata().getName() + "/templates/" + t.getName());
				Map<String, Object> currentContext = new HashMap<>(context);
				currentContext.put("Template", templateMap);

				factory.execute(t.getName(), currentContext, writer);
				String rendered = writer.toString();
				if (rendered != null && !rendered.isBlank()) {
					if (!rendered.trim().endsWith("---")) {
						sb.append(rendered);
						if (!rendered.endsWith("\n")) {
							sb.append('\n');
						}
						sb.append("---\n");
					}
					else {
						sb.append(rendered);
					}
				}
			}
			catch (StackOverflowError ex) {
				if (log.isErrorEnabled()) {
					log.error(
							"StackOverflowError rendering chart '{}', template '{}': recursive template inclusion detected",
							chart.getMetadata().getName(), t.getName());
				}
			}
			catch (Exception ex) {
				String chartName = chart.getMetadata().getName();
				if (log.isDebugEnabled()) {
					log.debug("Failed to render chart '{}', template '{}': {}", chartName, t.getName(),
							ex.getMessage());
				}
				throw new TemplateRenderException("Rendering failed: " + ex.getMessage(), ex, chartName, t.getName());
			}
		}
	}

	/**
	 * Merge each subchart's default values under its name/alias key so parent templates
	 * can access them via {@code .Values.<subchartName>.*}.
	 */
	@SuppressWarnings("unchecked")
	private void mergeSubchartDefaults(Chart chart, Map<String, Object> mergedValues) {
		for (Chart dep : chart.getDependencies()) {
			String depKey = (dep.getAlias() != null) ? dep.getAlias() : dep.getMetadata().getName();
			if (dep.getValues() != null && !dep.getValues().isEmpty()) {
				Map<String, Object> existing = (Map<String, Object>) mergedValues.getOrDefault(depKey, new HashMap<>());
				Map<String, Object> merged = mergeValues(dep.getValues(), existing);
				mergedValues.put(depKey, merged);
			}
		}
	}

	private Map<String, Object> mergeValues(Map<String, Object> defaults, Map<String, Object> overrides) {
		if (defaults == null) {
			return new HashMap<>(overrides);
		}
		if (overrides == null) {
			return new HashMap<>(defaults);
		}

		Map<String, Object> merged = new HashMap<>(defaults);
		for (Map.Entry<String, Object> entry : overrides.entrySet()) {
			Object overrideValue = entry.getValue();
			Object defaultValue = merged.get(entry.getKey());

			if (overrideValue instanceof Map<?, ?> && defaultValue instanceof Map<?, ?>) {
				@SuppressWarnings("unchecked")
				Map<String, Object> defMap = (Map<String, Object>) defaultValue;
				@SuppressWarnings("unchecked")
				Map<String, Object> overMap = (Map<String, Object>) overrideValue;
				merged.put(entry.getKey(), mergeValues(defMap, overMap));
			}
			else {
				merged.put(entry.getKey(), overrideValue);
			}
		}
		return merged;
	}

}
