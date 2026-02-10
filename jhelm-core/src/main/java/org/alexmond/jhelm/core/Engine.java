package org.alexmond.jhelm.core;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.StringTemplateSource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Engine {

    private final Handlebars handlebars;

    private final Map<String, String> namedTemplates = new HashMap<>();
    private final Map<String, Template> compiledTemplates = new HashMap<>();

    public Engine() {
        this.handlebars = new Handlebars();
        
        handlebars.registerHelper("range", (context, options) -> {
            if (context instanceof Iterable iter) {
                StringBuilder sb = new StringBuilder();
                for (Object item : iter) {
                    sb.append(options.fn(item));
                }
                return sb.toString();
            }
            if (context instanceof Map map) {
                StringBuilder sb = new StringBuilder();
                for (Object entry : map.entrySet()) {
                    sb.append(options.fn(entry));
                }
                return sb.toString();
            }
            return "";
        });

        handlebars.registerHelper("if", (context, options) -> {
            boolean condition = isTruthy(context);

            if (condition) {
                return options.fn(context);
            } else {
                return options.inverse(context);
            }
        });

        handlebars.registerHelper("not", (context, options) -> {
            return !isTruthy(context);
        });

        handlebars.registerHelper("and", (context, options) -> {
            for (Object param : options.params) {
                if (!isTruthy(param)) return false;
            }
            return isTruthy(context);
        });

        handlebars.registerHelper("or", (context, options) -> {
            if (isTruthy(context)) return true;
            for (Object param : options.params) {
                if (isTruthy(param)) return true;
            }
            return false;
        });

        handlebars.registerHelper("with", (context, options) -> {
            return options.fn(context);
        });

        handlebars.registerHelper("b64enc", (context, options) -> {
            if (context == null) return "";
            return java.util.Base64.getEncoder().encodeToString(context.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });

        handlebars.registerHelper("b64dec", (context, options) -> {
            if (context == null) return "";
            return new String(java.util.Base64.getDecoder().decode(context.toString()), java.nio.charset.StandardCharsets.UTF_8);
        });

        handlebars.registerHelper("default", (context, options) -> {
            Object defaultValue = context;
            Object value = options.param(0);
            return value != null ? value : defaultValue;
        });

        handlebars.registerHelper("indent", (context, options) -> {
            if (context == null) return "";
            Object param0 = options.param(0, 0);
            int spaces = 0;
            if (param0 instanceof Number n) spaces = n.intValue();
            else if (param0 instanceof String s) spaces = Integer.parseInt(s);
            String s = context.toString();
            return s.indent(spaces).stripTrailing();
        });

        handlebars.registerHelper("nindent", (context, options) -> {
            Object param0 = options.param(0, 0);
            int spaces = 0;
            if (param0 instanceof Number n) spaces = n.intValue();
            else if (param0 instanceof String s) spaces = Integer.parseInt(s);
            if (context == null) return "\n" + " ".repeat(spaces);
            String s = context.toString();
            return "\n" + s.indent(spaces).stripTrailing();
        });

        handlebars.registerHelper("empty", (context, options) -> !isTruthy(context));

        handlebars.registerHelper("hasKey", (context, options) -> {
            if (context instanceof Map m) {
                return m.containsKey(options.param(0));
            }
            return false;
        });

        handlebars.registerHelper("trimSuffix", (context, options) -> {
            if (context == null) return "";
            String suffix = options.param(0, "");
            String s = context.toString();
            if (s.endsWith(suffix)) {
                return s.substring(0, s.length() - suffix.length());
            }
            return s;
        });

        handlebars.registerHelper("cat", (context, options) -> {
            StringBuilder sb = new StringBuilder(context == null ? "" : context.toString());
            for (Object param : options.params) {
                sb.append(" ").append(param == null ? "" : param.toString());
            }
            return sb.toString();
        });

        handlebars.registerHelper("split", (context, options) -> {
            if (context == null) return new String[0];
            String sep = options.param(0, "");
            return context.toString().split(java.util.regex.Pattern.quote(sep));
        });

        handlebars.registerHelper("toYaml", (context, options) -> {
            try {
                return new com.fasterxml.jackson.dataformat.yaml.YAMLMapper().writeValueAsString(context);
            } catch (Exception e) {
                return "";
            }
        });

        handlebars.registerHelper("include", (context, options) -> {
            if (context == null) return "";
            String templateName = context.toString();
            // Helm allows unquoted names in template, but we mapped to quoted in regex
            templateName = templateName.replace("\"", "");
            
            // Track nesting depth for include
            Integer depth = options.data("include-depth");
            if (depth == null) depth = 0;
            if (depth > 10) { // Reduced depth limit
                log.warn("Too many nested includes (>10) for template {}", templateName);
                return "ERROR: Deep nesting";
            }

            // Extract actual template context from options.params or options.context
            Object templateContext = options.params.length > 0 ? options.params[0] : options.context;
            
            if (".".equals(templateContext) || templateContext == options.context) {
                templateContext = options.context;
            }
            try {
                String templateData = namedTemplates.get(templateName);
                if (templateData == null) {
                    log.debug("Template {} not found in namedTemplates", templateName);
                    return "";
                }
                
                Template template = compiledTemplates.get(templateName);
                if (template == null) {
                    template = handlebars.compile(new StringTemplateSource(templateName, templateData));
                    compiledTemplates.put(templateName, template);
                }
                return template.apply(com.github.jknack.handlebars.Context.copy(options.context, templateContext)
                        .data("include-depth", depth + 1));
            } catch (Throwable e) {
                // If it's a stack overflow or too deep nesting, we should stop
                if (e instanceof StackOverflowError) {
                    log.error("Stack overflow during include of {}: {}", templateName, e.getMessage());
                    return "ERROR: Stack Overflow";
                }
                // Avoid logging full stack trace for known common issues
                log.error("Failed to include template {}: {}", templateName, e.getMessage());
                return "ERROR: Include failed";
            }
        });

        handlebars.registerHelper("dict", (context, options) -> {
            Map<String, Object> map = new HashMap<>();
            if (context != null) {
                map.put(context.toString(), options.param(0));
            }
            for (int i = 1; i < options.params.length; i += 2) {
                if (i + 1 < options.params.length) {
                    map.put(options.params[i].toString(), options.params[i+1]);
                }
            }
            return map;
        });

        handlebars.registerHelper("tpl", (context, options) -> {
            if (context == null) return "";
            String tplData = context.toString();
            Object templateContext = options.params.length > 0 ? options.params[0] : options.context;
            try {
                String translated = translateGoTemplateToHbs(tplData);
                Template template = handlebars.compile(new StringTemplateSource("tpl-" + tplData.hashCode(), translated));
                return template.apply(templateContext);
            } catch (Throwable e) {
                log.error("Failed to render tpl: {}", e.getMessage());
                return "";
            }
        });

        handlebars.registerHelper("printf", (context, options) -> {
            if (context == null) return "";
            String format = context.toString();
            return String.format(format.replace("%v", "%s"), options.params);
        });

        handlebars.registerHelper("quote", (context, options) -> {
            if (context == null) return "\"\"";
            return "\"" + context.toString() + "\"";
        });

        handlebars.registerHelper("lower", (context, options) -> {
            return context == null ? "" : context.toString().toLowerCase();
        });

        handlebars.registerHelper("upper", (context, options) -> {
            return context == null ? "" : context.toString().toUpperCase();
        });

        handlebars.registerHelper("trunc", (context, options) -> {
            if (context == null) return "";
            int length = options.param(0, 0);
            String s = context.toString();
            if (s.length() <= length) return s;
            return s.substring(0, length);
        });

        handlebars.registerHelper("eq", (context, options) -> {
            Object other = options.param(0);
            if (context == null) return other == null;
            return context.toString().equals(other == null ? null : other.toString());
        });

        handlebars.registerHelper("ne", (context, options) -> {
            Object other = options.param(0);
            if (context == null) return other != null;
            return !context.toString().equals(other == null ? null : other.toString());
        });

        handlebars.registerHelper("toString", (context, options) -> {
            return context == null ? "" : context.toString();
        });

        handlebars.registerHelper("first", (context, options) -> {
            if (context instanceof List list && !list.isEmpty()) return list.get(0);
            if (context != null && context.getClass().isArray()) {
                Object[] arr = (Object[]) context;
                if (arr.length > 0) return arr[0];
            }
            return context;
        });

        handlebars.registerHelper("omit", (context, options) -> {
            if (context instanceof Map map) {
                Map<String, Object> result = new HashMap<>(map);
                for (Object param : options.params) {
                    result.remove(param.toString());
                }
                return result;
            }
            return context;
        });
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
        compiledTemplates.clear();
        // Collect all named templates (define blocks) first
        collectNamedTemplates(chart);
        log.info("Collected {} named templates", namedTemplates.size());
        
        try {
            // Using a shared set for the whole rendering process to avoid redundant work and loops
            java.util.Set<String> renderedCharts = new java.util.HashSet<>();
            return renderWithSubcharts(chart, values, releaseInfo, renderedCharts, 0);
        } catch (StackOverflowError e) {
            log.error("Global StackOverflowError during rendering of chart {}: {}", chart.getMetadata().getName(), e.getMessage());
            return "ERROR: Recursive template inclusion or too deep nesting";
        }
    }

    private void collectNamedTemplates(Chart chart) {
        for (Chart.Template t : chart.getTemplates()) {
            if (t.getName().endsWith(".tpl") || t.getData().contains("{{ define")) {
                processDefines(t.getData());
            }
        }
        for (Chart subchart : chart.getDependencies()) {
            collectNamedTemplates(subchart);
        }
    }

    private void processDefines(String data) {
        // Helm define blocks can be {{ define "name" }}...{{ end }}
        // The regex should be non-greedy for the content and handle potential whitespace
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{(-?)\\s*define\\s+\"(.*?)\"\\s*(-?)\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(data);
        while (matcher.find()) {
            String name = matcher.group(2);
            int start = matcher.end();
            
            // Find the matching end
            int end = findMatchingEnd(data, start);
            if (end != -1) {
                String content = data.substring(start, end);
                log.info("Defining template: {} (length: {})", name, content.length());
                namedTemplates.put(name, translateGoTemplateToHbs(content));
            }
        }
    }

    private int findMatchingEnd(String data, int start) {
        int depth = 1;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\{\\{(-?)\\s*(if|range|with|define|end)\\b");
        java.util.regex.Matcher m = p.matcher(data);
        m.region(start, data.length());
        
        while (m.find()) {
            String tag = m.group(2);
            if ("end".equals(tag)) {
                depth--;
                if (depth == 0) return m.start();
            } else {
                depth++;
            }
        }
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
                    String templateData = translateGoTemplateToHbs(t.getData());
                    Template hbsTemplate = handlebars.compile(new StringTemplateSource(t.getName(), templateData));
                    
                    // Template name in context should be current template
                    Map<String, Object> templateMap = new HashMap<>((Map<String, Object>)context.get("Template"));
                    templateMap.put("Name", chart.getMetadata().getName() + "/templates/" + t.getName());
                    Map<String, Object> currentContext = new HashMap<>(context);
                    currentContext.put("Template", templateMap);

                    String rendered = hbsTemplate.apply(currentContext);
                    if (rendered != null && !rendered.trim().isEmpty()) {
                        sb.append(rendered).append("\n---\n");
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
            
            // If subchart is disabled, skip it
            if (subchartOverrides.containsKey("enabled") && !isTruthy(subchartOverrides.get("enabled"))) {
                log.info("Subchart {} is disabled", subchartName);
                continue;
            }

            // In Helm, global values are shared
            if (mergedValues.containsKey("global")) {
                subchartOverrides.put("global", mergedValues.get("global"));
            }

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
            String templateData = template.getData();
            
            // Map Go template tags to Handlebars tags
            // Use a more sophisticated approach to handle nested tags and generic 'end'
            templateData = translateGoTemplateToHbs(templateData);
            log.info("Translated template {}:\n{}", template.getName(), templateData);
            
            try {
                Template hbsTemplate = handlebars.compile(new StringTemplateSource(template.getName(), templateData));
                return hbsTemplate.apply(context);
            } catch (Exception e) {
                log.error("Handlebars compilation/execution failed for {}. Template data:\n{}", template.getName(), templateData);
                throw e;
            }
        } catch (IOException e) {
            log.error("Failed to render template {}: {}", template.getName(), e.getMessage());
            throw new RuntimeException("Failed to render template " + template.getName(), e);
        }
    }

    private String translateGoTemplateToHbs(String data) {
        if (data == null) return "";
        // Simple heuristic to avoid translating extremely large or already translated templates
        if (data.length() > 50000) {
            log.warn("Template data too large ({}), skipping full translation", data.length());
            return data;
        }
        // Strip Go comments
        data = data.replaceAll("\\{\\{\\s*/\\*.*?\\*/\\s*\\}\\}", "");
        
        // Strip assignment blocks like {{ $foo := ... }} or {{ $k, $v := ... }}
        // and also handle {{ $foo = ... }}
        data = data.replaceAll("\\{\\{(-?)\\s*\\$.*?\\s*[:=]=?.*?(-?)\\}\\}", "");
        
        // Strip the variable part in range if it exists: {{ range $k, $v := .Values }} -> {{ range .Values }}
        data = data.replaceAll("range\\s+\\$.*?,\\s*\\$.*?\\s*[:=]=?\\s*", "range ");
        data = data.replaceAll("range\\s+\\$.*?\\s*[:=]=?\\s*", "range ");

        // Handle {{- if ... }} or {{- range ... }} with whitespace control
        data = data.replaceAll("\\{\\{\\s*-\\s*", "{{");
        data = data.replaceAll("\\s*-\\s*\\}\\}", "}}");

        StringBuilder sb = new StringBuilder();
        java.util.Stack<String> stack = new java.util.Stack<>();
        
        // Match both with and without whitespace control (the -)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{(-?)\\s*(range|if|else|end|with|template|include|define)?\\s*(.*?)\\s*(-?)\\}\\}", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(data);
        
        int lastEnd = 0;
        while (matcher.find()) {
            sb.append(data, lastEnd, matcher.start());
            
            String command = matcher.group(2);
            String args = matcher.group(3).trim();
            
            if ("range".equals(command)) {
                stack.push("range");
                sb.append("{{#range ").append(translateExpression(args)).append("}}");
            } else if ("if".equals(command)) {
                stack.push("if");
                sb.append("{{#if ").append(translateExpression(args)).append("}}");
            } else if ("with".equals(command)) {
                stack.push("with");
                sb.append("{{#with ").append(translateExpression(args)).append("}}");
            } else if ("else".equals(command)) {
                if (args.startsWith("if ")) {
                    sb.append("{{else}}{{#if ").append(translateExpression(args.substring(3).trim())).append("}}");
                    stack.push("if");
                } else {
                    sb.append("{{else}}");
                }
            } else if ("end".equals(command)) {
                if (stack.isEmpty()) {
                    log.debug("Unmatched end tag found in template");
                    continue;
                }
                String top = stack.pop();
                if ("define".equals(top)) {
                    sb.append("{{/if}}");
                } else {
                    sb.append("{{/").append(top).append("}}");
                }
            } else if ("define".equals(command)) {
                // Ignore define blocks entirely for Handlebars translation
                // since they are collected separately
                stack.push("define");
                sb.append("{{#if false}}");
            } else if ("template".equals(command) || "include".equals(command)) {
                String translated = translateExpression(command + " " + args);
                if (translated.startsWith("(include ") && translated.endsWith(")")) {
                    translated = translated.substring(1, translated.length() - 1);
                } else if (translated.startsWith("include ")) {
                    // already starts with include
                } else {
                    translated = "include " + translated;
                }
                sb.append("{{").append(translated).append("}}");
            } else if (command == null || command.isEmpty()) {
                if (args.startsWith("/*") && args.endsWith("*/")) {
                    // ignore
                } else {
                    String translated = translateExpression(args);
                    if (translated != null && !translated.isEmpty()) {
                        if (translated.startsWith("tpl ")) {
                            translated = translated.replaceFirst("tpl\\s+", "include ");
                        }
                        sb.append("{{").append(translated).append("}}");
                    }
                }
            } else {
                sb.append(matcher.group(0));
            }
            
            lastEnd = matcher.end();
        }
        sb.append(data.substring(lastEnd));
        return sb.toString();
    }

    private String translateExpression(String expr) {
        if (expr == null || expr.isEmpty() || expr.equals(".")) return expr;
        if (expr.equals("$")) return "this";
        
        // Handle ( ... )
        if (expr.startsWith("(") && expr.endsWith(")")) {
            String inner = expr.substring(1, expr.length() - 1).trim();
            String translated = translateExpression(inner);
            if (translated.startsWith("(include ") && translated.endsWith(")")) {
                return translated;
            }
            if (translated.startsWith("include ")) {
                return "(" + translated + ")";
            }
            return "(" + translated + ")";
        }

        // Handle quoted strings
        if (expr.startsWith("\"") && expr.endsWith("\"")) {
            return expr;
        }

        // Handle pipelines in expressions
        if (expr.contains("|")) {
             String[] pipeParts = expr.split("\\|", 2);
             String var = translateExpression(pipeParts[0].trim());
             String funcPart = pipeParts[1].trim();
             
             String[] funcAndArgs = funcPart.split("\\s+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
             StringBuilder res = new StringBuilder();
             res.append(funcAndArgs[0]);
             res.append(" ");
             
             if (var.startsWith("include ") || var.startsWith("(include ")) {
                 res.append(var);
             } else if (var.contains(" ")) {
                 res.append("(").append(var).append(")");
             } else {
                 res.append(var);
             }
             for (int i = 1; i < funcAndArgs.length; i++) {
                 res.append(" ").append(translateExpression(funcAndArgs[i]));
             }
             return res.toString().trim();
        }

        // Split into parts to handle functions and their arguments
        String[] parts = expr.split("\\s+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        if (parts.length > 1) {
            String first = parts[0];
            if ("include".equals(first) || "template".equals(first)) {
                StringBuilder sb = new StringBuilder("(include ");
                for (int i = 1; i < parts.length; i++) {
                    sb.append(translateExpression(parts[i])).append(" ");
                }
                return sb.toString().trim() + ")";
            }
            if ("and".equals(first) || "or".equals(first) || "not".equals(first)) {
                StringBuilder sb = new StringBuilder(first).append(" ");
                for (int i = 1; i < parts.length; i++) {
                    String translated = translateExpression(parts[i]);
                    if (translated.contains(" ") && !translated.startsWith("\"")) {
                        sb.append("(").append(translated).append(") ");
                    } else {
                        sb.append(translated).append(" ");
                    }
                }
                return "(" + sb.toString().trim() + ")";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String translated = translateExpression(parts[i]);
                if (translated.contains(" ") && !translated.startsWith("\"")) {
                    sb.append("(").append(translated).append(") ");
                } else {
                    sb.append(translated).append(" ");
                }
            }
            return sb.toString().trim();
        }

        return stripDot(expr);
    }

    private String stripDot(String s) {
        if (s != null && s.startsWith(".")) {
            return s.substring(1);
        }
        return s;
    }
}
