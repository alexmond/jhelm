package org.alexmond.jhelm.core;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.GoTemplateFactory;
import org.alexmond.jhelm.gotemplate.internal.ast.Node;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Engine {

    private final Map<String, String> namedTemplates = new HashMap<>();
    private GoTemplateFactory factory;

    public Engine() {
    }

    private boolean isTruthy(Object context) {
        if (context == null) return false;
        if (context instanceof Boolean b) return b;
        if (context instanceof String s) return !s.isEmpty();
        if (context instanceof Iterable i) return i.iterator().hasNext();
        if (context instanceof Map m) return !m.isEmpty();
        if (context instanceof Number n) return n.doubleValue() != 0;
        return true;
    }

    @SneakyThrows
    public String render(Chart chart, Map<String, Object> values, Map<String, Object> releaseInfo) {
        namedTemplates.clear();
        // Create a new factory for each render to avoid accumulation
        this.factory = new GoTemplateFactory();
        // Collect all named templates (define blocks) first
        collectNamedTemplates(chart);

        try {
            // Using a shared set for the whole rendering process to avoid redundant work and loops
            java.util.Set<String> renderedCharts = new java.util.HashSet<>();
            String rendered = renderWithSubcharts(chart, values, releaseInfo, renderedCharts, 0);
            return cleanManifest(rendered);
        } catch (StackOverflowError e) {
            log.error("Global StackOverflowError during rendering of chart {}: {}", chart.getMetadata().getName(), e.getMessage());
            return "ERROR: Recursive template inclusion or too deep nesting";
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
            // Skip empty documents, standalone separators, and documents that are just comments after a separator
            if (!trimmed.isEmpty() && !trimmed.equals("---") && !trimmed.startsWith("---")) {
                if (cleaned.length() > 0) {
                    cleaned.append("\n---\n");
                }
                cleaned.append(trimmed);
            }
        }

        // Add final newline if there's content
        if (cleaned.length() > 0) {
            cleaned.append("\n");
        }

        return cleaned.toString();
    }

    private void collectNamedTemplates(Chart chart) {
        // First pass: collect all templates without parsing, to have them available for definitions
        // Also track which chart each template belongs to for proper namespacing
        Map<String, String> allTemplates = new HashMap<>();
        Map<String, String> templateToChartName = new HashMap<>();
        collectAllTemplates(chart, allTemplates, templateToChartName, chart.getMetadata().getName());

        // Second pass: try to parse each template. Templates with definitions (tpl) should be parsed first.
        List<String> sortedNames = allTemplates.keySet().stream()
                .sorted((a, b) -> {
                    boolean aTpl = a.endsWith(".tpl");
                    boolean bTpl = b.endsWith(".tpl");
                    if (aTpl && !bTpl) return -1;
                    if (!aTpl && bTpl) return 1;
                    return a.compareTo(b);
                })
                .toList();

        // Store original rootNodes count
        int beforeCount = factory.getRootNodes().size();

        for (String name : sortedNames) {
            try {
                String chartName = templateToChartName.get(name);
                factory.parse(name, allTemplates.get(name));
                log.info("Parsed template: {} (from chart: {})", name, chartName);

                // After parsing, check if new named templates (defines) were added
                // If this is a _helpers.tpl from a subchart, we need to alias the templates
                if (name.contains("_helpers") || name.endsWith(".tpl")) {
                    int afterCount = factory.getRootNodes().size();
                    if (afterCount > beforeCount) {
                        // New named templates were added
                        // We need to create aliases with chart prefix
                        createChartPrefixedAliases(chartName, beforeCount, afterCount);
                    }
                    beforeCount = afterCount;
                }
            } catch (Exception e) {
                log.warn("Parse failure for {}: {}", name, e.getMessage());
            }
        }

        // Count how many named templates (defines) we actually collected
        int namedTemplateCount = factory.getRootNodes().size() - allTemplates.size();
        log.info("Collected {} named templates from {} files", namedTemplateCount, allTemplates.size());
    }

    private void createChartPrefixedAliases(String chartName, int beforeCount, int afterCount) {
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
                // Add alias: prefixedName -> same node as templateName
                Node node = rootNodes.get(templateName);
                if (node != null && !rootNodes.containsKey(prefixedName)) {
                    rootNodes.put(prefixedName, node);
                    log.debug("Created template alias: {} -> {}", prefixedName, templateName);
                }
            }
        }
    }

    private void collectAllTemplates(Chart chart, Map<String, String> templates, Map<String, String> templateToChartName, String rootChartName) {
        String chartName = chart.getMetadata().getName();

        for (Chart.Template t : chart.getTemplates()) {
            // Use chart-prefixed key to avoid collisions between charts with same template names
            String uniqueKey = chartName + ":" + t.getName();
            templates.put(uniqueKey, t.getData());
            templateToChartName.put(uniqueKey, chartName);
        }

        for (Chart subchart : chart.getDependencies()) {
            collectAllTemplates(subchart, templates, templateToChartName, rootChartName);
        }
    }

    private void processDefines(String data) {
    }

    private int findMatchingEnd(String data, int start) {
        return -1;
    }

    private String renderWithSubcharts(Chart chart, Map<String, Object> values, Map<String, Object> releaseInfo, java.util.Set<String> renderedCharts, int depth) {
        String chartKey = chart.getMetadata().getName() + ":" + chart.getMetadata().getVersion();
        if (renderedCharts.contains(chartKey)) {
            log.debug("Chart {} already rendered in this path, skipping to avoid recursion", chartKey);
            return "";
        }
        if (depth > 3) { // Even further reduced depth
            log.warn("Subchart nesting depth too high (>3) for chart {}", chartKey);
            return "";
        }
        renderedCharts.add(chartKey);

        // Merge chart default values with provided values
        Map<String, Object> chartValues = chart.getValues() != null ? chart.getValues() : new HashMap<>();
        Map<String, Object> mergedValues = mergeValues(chartValues, values);

        Map<String, Object> context = new HashMap<>();
        context.put("Values", mergedValues);
        context.put("Chart", chart.getMetadata());
        context.put("Release", releaseInfo);

        // Add standard Helm objects
        context.put("Capabilities", Map.of(
                "KubeVersion", Map.of("Version", "v1.28.0", "Major", "1", "Minor", "28"),
                "APIVersions", List.of("v1", "apps/v1", "networking.k8s.io/v1")
        ));
        context.put("Template", Map.of("Name", "", "BasePath", "templates"));

        StringBuilder sb = new StringBuilder();

        // Render current chart templates
        for (Chart.Template t : chart.getTemplates()) {
            if (t.getName().endsWith(".yaml")) {
                try {
                    factory.parse(t.getName(), t.getData());
                    GoTemplate template = factory.getTemplate(t.getName());

                    // Template name in context should be current template
                    Map<String, Object> templateMap = new HashMap<>((Map<String, Object>) context.get("Template"));
                    templateMap.put("Name", chart.getMetadata().getName() + "/templates/" + t.getName());
                    Map<String, Object> currentContext = new HashMap<>(context);
                    currentContext.put("Template", templateMap);

                    StringWriter writer = new StringWriter();
                    template.execute(currentContext, writer);
                    String rendered = writer.toString();
                    if (rendered != null && !rendered.trim().isEmpty()) {
                        // If template already ends with document separator, don't add another
                        if (!rendered.trim().endsWith("---")) {
                            sb.append(rendered);
                            if (!rendered.endsWith("\n")) {
                                sb.append("\n");
                            }
                            sb.append("---\n");
                        } else {
                            sb.append(rendered);
                        }
                    }
                } catch (StackOverflowError e) {
                    log.error("StackOverflowError rendering template {}: {}", t.getName(), e.getMessage());
                    // Skip this template but don't fail the whole chart if possible
                } catch (Exception e) {
                    log.error("Failed to render template {}: {}", t.getName(), e.getMessage());
                    throw new RuntimeException("Failed to render template " + t.getName(), e);
                }
            }
        }

        // Render subcharts
        for (Chart subchart : chart.getDependencies()) {
            String subchartName = subchart.getMetadata().getName();

            // Subcharts are only rendered if enabled in Values
            Map<String, Object> subchartOverrides = (Map<String, Object>) mergedValues.getOrDefault(subchartName, new HashMap<>());

            log.debug("Subchart {}: overrides={}, enabled={}", subchartName, subchartOverrides, subchartOverrides.get("enabled"));

            // If subchart is disabled, skip it
            if (subchartOverrides.containsKey("enabled") && !isTruthy(subchartOverrides.get("enabled"))) {
                log.info("Subchart {} is disabled", subchartName);
                continue;
            }

            // In Helm, global values are shared
            if (mergedValues.containsKey("global")) {
                subchartOverrides.put("global", mergedValues.get("global"));
            }

            log.info("Rendering subchart: {}", subchartName);
            sb.append(renderWithSubcharts(subchart, subchartOverrides, releaseInfo, renderedCharts, depth + 1));
        }

        return sb.toString();
    }

    private Map<String, Object> mergeValues(Map<String, Object> defaults, Map<String, Object> overrides) {
        if (defaults == null) return new HashMap<>(overrides);
        if (overrides == null) return new HashMap<>(defaults);

        Map<String, Object> merged = new HashMap<>(defaults);
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            Object overrideValue = entry.getValue();
            Object defaultValue = merged.get(entry.getKey());

            if (overrideValue instanceof Map overMap && defaultValue instanceof Map defMap) {
                merged.put(entry.getKey(), mergeValues(defMap, overMap));
            } else {
                merged.put(entry.getKey(), overrideValue);
            }
        }
        return merged;
    }

    private String renderTemplate(Chart.Template template, Map<String, Object> context) {
        try {
            factory.parse(template.getName(), template.getData());
            GoTemplate goTemplate = factory.getTemplate(template.getName());
            StringWriter writer = new StringWriter();
            goTemplate.execute(context, writer);
            return writer.toString();
        } catch (Exception e) {
            log.error("Template rendering failed for {}. Error: {}", template.getName(), e.getMessage());
            throw new RuntimeException("Failed to render template " + template.getName(), e);
        }
    }

    private String stripDot(String s) {
        if (s != null && s.startsWith(".")) {
            return s.substring(1);
        }
        return s;
    }
}
