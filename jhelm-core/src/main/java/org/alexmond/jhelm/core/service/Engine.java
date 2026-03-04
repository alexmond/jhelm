package org.alexmond.jhelm.core.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.cache.TemplateCache;
import org.alexmond.jhelm.core.exception.SchemaValidationException;
import org.alexmond.jhelm.core.exception.TemplateRenderException;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.parse.Node;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartFiles;
import org.alexmond.jhelm.core.model.Dependency;
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

	private final Map<String, String> templateVersions = new HashMap<>();

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
		templateVersions.clear();
		// Create a new template for each render to avoid accumulation
		this.factory = new GoTemplate();

		// Apply aliases from dependency metadata before collecting templates, so that
		// subchart .Chart.Name and template registration keys use the alias consistently.
		applyAliasesFromMetadata(chart);

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
		String chartVersion = chart.getMetadata().getVersion();

		for (Chart.Template t : chart.getTemplates()) {
			// Use Helm-style path (chartName/templates/fileName) to match the names
			// that charts use with $.Template.BasePath in include calls
			String helmStyleKey = chartName + "/templates/" + t.getName();
			// On collision, keep the template from the higher chart version. Newer
			// library chart versions are backward-compatible supersets of older ones,
			// so keeping the newest avoids missing-feature failures.
			String existingVersion = templateVersions.get(helmStyleKey);
			if (existingVersion == null || compareVersions(chartVersion, existingVersion) > 0) {
				templates.put(helmStyleKey, t.getData());
				templateToChartName.put(helmStyleKey, chartName);
				templateVersions.put(helmStyleKey, chartVersion);
			}
		}

		for (Chart subchart : chart.getDependencies()) {
			collectAllTemplates(subchart, templates, templateToChartName, rootChartName);
		}
	}

	/**
	 * Compare two version strings by splitting on "." and comparing each segment
	 * numerically. Returns positive if v1 &gt; v2, negative if v1 &lt; v2, zero if equal.
	 */
	static int compareVersions(String v1, String v2) {
		if (v1 == null && v2 == null) {
			return 0;
		}
		if (v1 == null) {
			return -1;
		}
		if (v2 == null) {
			return 1;
		}
		String[] parts1 = v1.split("\\.");
		String[] parts2 = v2.split("\\.");
		int len = Math.max(parts1.length, parts2.length);
		for (int i = 0; i < len; i++) {
			int n1 = (i < parts1.length) ? parseSegment(parts1[i]) : 0;
			int n2 = (i < parts2.length) ? parseSegment(parts2[i]) : 0;
			if (n1 != n2) {
				return Integer.compare(n1, n2);
			}
		}
		return 0;
	}

	private static int parseSegment(String segment) {
		try {
			return Integer.parseInt(segment);
		}
		catch (NumberFormatException ex) {
			// Strip non-numeric suffix (e.g. "1-beta") and parse the leading digits
			StringBuilder digits = new StringBuilder();
			for (char ch : segment.toCharArray()) {
				if (Character.isDigit(ch)) {
					digits.append(ch);
				}
				else {
					break;
				}
			}
			if (digits.length() > 0) {
				return Integer.parseInt(digits.toString());
			}
			return 0;
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

		// Process import-values to enrich chart defaults from subchart values
		Map<String, Object> chartValues = (chart.getValues() != null) ? new HashMap<>(chart.getValues())
				: new HashMap<>();
		processImportValues(chart, chartValues);

		// Merge chart default values with provided values
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
		context.put("Capabilities", Map.of("KubeVersion",
				Map.of("Version", "v1.35.0", "Major", "1", "Minor", "35", "GitVersion", "v1.35.0"), "HelmVersion",
				Map.of("Version", "v3.16.0", "GitCommit", "", "GitTreeState", "", "GoVersion", ""), "APIVersions",
				new VersionSet(DEFAULT_API_VERSIONS)));
		String chartBasePath = chart.getMetadata().getName() + "/templates";
		context.put("Template", Map.of("Name", "", "BasePath", chartBasePath));
		context.put("Files", new ChartFiles(chart.getFiles()));

		// Build .Subcharts map: subchart name/alias → full context (Chart, Values,
		// Release)
		// In Helm, .Subcharts.<name> provides a complete rendering context so that
		// templates referenced via include can access .Values, .Release, etc.
		Map<String, Object> subcharts = new HashMap<>();
		for (Chart dep : chart.getDependencies()) {
			String depKey = (dep.getAlias() != null) ? dep.getAlias() : dep.getMetadata().getName();
			@SuppressWarnings("unchecked")
			Map<String, Object> subchartValues = (Map<String, Object>) mergedValues.getOrDefault(depKey,
					new HashMap<>());
			Map<String, Object> subchartContext = new HashMap<>();
			subchartContext.put("Chart", dep.getMetadata());
			subchartContext.put("Values", subchartValues);
			subchartContext.put("Release", releaseInfo);
			subcharts.put(depKey, subchartContext);
		}
		context.put("Subcharts", subcharts);

		StringBuilder sb = new StringBuilder();

		// Render current chart templates
		renderChartTemplates(chart, context, sb);

		// Render subcharts
		List<Dependency> parentDepMeta = (chart.getMetadata() != null) ? chart.getMetadata().getDependencies() : null;
		for (Chart subchart : chart.getDependencies()) {
			// Use alias as lookup key when set (alias takes precedence over chart name)
			String subchartName = (subchart.getAlias() != null) ? subchart.getAlias()
					: subchart.getMetadata().getName();

			// Evaluate dependency condition from Chart.yaml/requirements.yaml (e.g.
			// "datadog.operator.enabled"). Condition paths are checked against the
			// PARENT chart's merged values. If the condition evaluates to false the
			// subchart is skipped.
			Dependency depMeta = findDependencyMetadata(parentDepMeta, subchart);
			if (depMeta != null && depMeta.getCondition() != null && !depMeta.getCondition().isEmpty()) {
				if (!evaluateDependencyCondition(depMeta.getCondition(), mergedValues)) {
					if (log.isDebugEnabled()) {
						log.debug("Subchart {} disabled by condition '{}'", subchartName, depMeta.getCondition());
					}
					continue;
				}
			}

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

			// In Helm, global values are deep-merged: parent globals override subchart
			// globals
			if (mergedValues.containsKey("global")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> parentGlobal = (Map<String, Object>) mergedValues.get("global");
				@SuppressWarnings("unchecked")
				Map<String, Object> subGlobal = (Map<String, Object>) subchartOverrides.getOrDefault("global",
						new HashMap<>());
				subchartOverrides.put("global", mergeValues(subGlobal, parentGlobal));
			}

			if (log.isDebugEnabled()) {
				log.debug("Rendering subchart: {}", subchartName);
			}
			sb.append(renderWithSubcharts(subchart, subchartOverrides, releaseInfo, renderedCharts, depth + 1));
		}

		return sb.toString();
	}

	private void renderChartTemplates(Chart chart, Map<String, Object> context, StringBuilder sb) {
		// Library charts only provide named templates via include — do not render
		// their .yaml files as standalone resources
		if ("library".equals(chart.getMetadata().getType())) {
			return;
		}
		// Helm 4 executes templates in reverse alphabetical order so that
		// zzz_*.yaml setup templates (e.g. istiod's zzz_profile.yaml which
		// merges _internal_defaults into .Values) run before other templates.
		List<Chart.Template> sorted = new ArrayList<>(chart.getTemplates());
		sorted.sort(Comparator.comparing(Chart.Template::getName, Comparator.reverseOrder()));
		for (Chart.Template t : sorted) {
			if (!t.getName().endsWith(".yaml") && !t.getName().endsWith(".yml")) {
				continue;
			}
			try {
				String helmStyleName = chart.getMetadata().getName() + "/templates/" + t.getName();
				parseWithCache(helmStyleName, t.getData());
				StringWriter writer = new StringWriter();

				// Share context across all template executions within a chart so that
				// cross-template mutations via set $ "Values" propagate (Go Helm
				// behavior)
				@SuppressWarnings("unchecked")
				Map<String, Object> templateMap = new HashMap<>((Map<String, Object>) context.get("Template"));
				templateMap.put("Name", helmStyleName);
				context.put("Template", templateMap);

				factory.execute(helmStyleName, context, writer);
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

	/**
	 * Process {@code import-values} directives from chart dependency metadata. For each
	 * dependency that declares {@code import-values}, values are extracted from the
	 * subchart's defaults and placed into the parent chart's defaults.
	 *
	 * <p>
	 * Two forms are supported:
	 * <ul>
	 * <li>String: imports from subchart's {@code exports.<value>} into parent root</li>
	 * <li>Map with {@code child}/{@code parent}: imports from subchart path to parent
	 * path</li>
	 * </ul>
	 */
	@SuppressWarnings("unchecked")
	private void processImportValues(Chart chart, Map<String, Object> chartValues) {
		List<Dependency> depMetadata = (chart.getMetadata() != null) ? chart.getMetadata().getDependencies() : null;
		if (depMetadata == null || depMetadata.isEmpty()) {
			return;
		}
		for (Dependency dep : depMetadata) {
			if (dep.getImportValues() == null || dep.getImportValues().isEmpty()) {
				continue;
			}
			String depKey = (dep.getAlias() != null && !dep.getAlias().isEmpty()) ? dep.getAlias() : dep.getName();
			Chart subchart = findSubchart(chart, depKey);
			if (subchart == null) {
				continue;
			}
			Map<String, Object> subValues = (subchart.getValues() != null) ? subchart.getValues() : Map.of();
			for (Object iv : dep.getImportValues()) {
				if (iv instanceof Map<?, ?> m) {
					String child = String.valueOf(m.get("child"));
					String parent = String.valueOf(m.get("parent"));
					Object value = getNestedValue(subValues, child);
					if (value != null) {
						setNestedValue(chartValues, parent, value);
					}
				}
				else if (iv instanceof String s) {
					Object value = getNestedValue(subValues, "exports." + s);
					if (value instanceof Map<?, ?> exportedMap) {
						for (Map.Entry<?, ?> entry : exportedMap.entrySet()) {
							chartValues.putIfAbsent(String.valueOf(entry.getKey()), entry.getValue());
						}
					}
				}
			}
		}
	}

	private Chart findSubchart(Chart chart, String key) {
		for (Chart dep : chart.getDependencies()) {
			String name = (dep.getAlias() != null) ? dep.getAlias() : dep.getMetadata().getName();
			if (key.equals(name)) {
				return dep;
			}
		}
		return null;
	}

	private Object getNestedValue(Map<String, Object> map, String path) {
		String[] parts = path.split("\\.");
		Object current = map;
		for (String part : parts) {
			if (!(current instanceof Map<?, ?>)) {
				return null;
			}
			current = ((Map<?, ?>) current).get(part);
			if (current == null) {
				return null;
			}
		}
		return current;
	}

	@SuppressWarnings("unchecked")
	private void setNestedValue(Map<String, Object> map, String path, Object value) {
		String[] parts = path.split("\\.");
		Map<String, Object> current = map;
		for (int i = 0; i < parts.length - 1; i++) {
			Object next = current.get(parts[i]);
			if (!(next instanceof Map<?, ?>)) {
				Map<String, Object> newMap = new HashMap<>();
				current.put(parts[i], newMap);
				current = newMap;
			}
			else {
				current = (Map<String, Object>) next;
			}
		}
		String lastKey = parts[parts.length - 1];
		current.putIfAbsent(lastKey, value);
	}

	/**
	 * Apply aliases from dependency metadata to subchart Chart objects. In Helm, when a
	 * dependency declares {@code alias: operator}, the subchart's {@code .Chart.Name}
	 * becomes the alias. This must be applied before template collection so that template
	 * keys and rendering context use the alias consistently.
	 */
	private void applyAliasesFromMetadata(Chart chart) {
		List<Dependency> depMeta = (chart.getMetadata() != null) ? chart.getMetadata().getDependencies() : null;
		if (depMeta == null) {
			return;
		}
		for (Chart subchart : chart.getDependencies()) {
			Dependency dep = findDependencyMetadata(depMeta, subchart);
			if (dep != null && dep.getAlias() != null && !dep.getAlias().isEmpty()) {
				subchart.setAlias(dep.getAlias());
				subchart.getMetadata().setName(dep.getAlias());
			}
			// Recurse into nested subcharts
			applyAliasesFromMetadata(subchart);
		}
	}

	/**
	 * Find the {@link Dependency} metadata entry that corresponds to the given subchart.
	 */
	private Dependency findDependencyMetadata(List<Dependency> deps, Chart subchart) {
		if (deps == null) {
			return null;
		}
		String chartName = subchart.getMetadata().getName();
		String chartAlias = subchart.getAlias();
		for (Dependency dep : deps) {
			if (dep.getAlias() != null && dep.getAlias().equals(chartAlias)) {
				return dep;
			}
			if (dep.getName().equals(chartName)) {
				return dep;
			}
		}
		return null;
	}

	/**
	 * Evaluate a dependency condition expression against chart values. Condition is a
	 * comma-separated list of dot-separated value paths. The first path that resolves to
	 * a boolean value wins. If no path resolves, the dependency is included by default.
	 */
	private boolean evaluateDependencyCondition(String condition, Map<String, Object> values) {
		String[] paths = condition.split(",");
		for (String path : paths) {
			path = path.trim();
			String[] parts = path.split("\\.");
			Object current = values;
			boolean found = true;
			for (String part : parts) {
				if (!(current instanceof Map<?, ?>)) {
					found = false;
					break;
				}
				current = ((Map<?, ?>) current).get(part);
				if (current == null) {
					found = false;
					break;
				}
			}
			if (found) {
				if (current instanceof Boolean b) {
					return b;
				}
				if (current instanceof String s) {
					return Boolean.parseBoolean(s);
				}
			}
		}
		// No condition path resolved — include by default
		return true;
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
