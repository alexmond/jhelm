package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.Function;
import org.alexmond.jhelm.gotemplate.GoTemplate;
import org.alexmond.jhelm.gotemplate.GoTemplate;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Helm template-specific functions for template inclusion and evaluation
 * Based on: <a href="https://helm.sh/docs/chart_template_guide/function_list/">https://helm.sh/docs/chart_template_guide/function_list/</a>
 */
public class TemplateFunctions {

    /**
     * Get template functions that require access to the GoTemplate
     *
     * @param factory The template factory for template lookups
     * @return Map of function name to Function implementation
     */
    public static Map<String, Function> getFunctions(GoTemplate factory) {
        Map<String, Function> functions = new HashMap<>();

        functions.put("include", include(factory));
        functions.put("mustInclude", mustInclude(factory));
        functions.put("tpl", tpl(factory));
        functions.put("mustTpl", mustTpl(factory));
        functions.put("required", required());

        return functions;
    }

    /**
     * include executes a named template and returns its output as a string
     * Syntax: include "templateName" $context
     * Returns empty string on error
     */
    private static Function include(GoTemplate factory) {
        return args -> {
            if (args.length < 2) return "";
            String name = String.valueOf(args[0]);
            Object data = args[1];
            try {
                StringWriter writer = new StringWriter();
                factory.execute(name, data, writer);
                return writer.toString();
            } catch (Exception e) {
                // Log warning but return empty string for compatibility
                return "";
            }
        };
    }

    /**
     * mustInclude executes a named template and returns its output as a string
     * Syntax: mustInclude "templateName" $context
     * Throws exception on error
     */
    private static Function mustInclude(GoTemplate factory) {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("mustInclude: insufficient arguments (requires template name and context)");
            }
            String name = String.valueOf(args[0]);
            Object data = args[1];
            try {
                StringWriter writer = new StringWriter();
                factory.execute(name, data, writer);
                return writer.toString();
            } catch (Exception e) {
                throw new RuntimeException("mustInclude: failed to execute template '" + name + "': " + e.getMessage(), e);
            }
        };
    }

    /**
     * tpl evaluates a string as a template inline
     * Syntax: tpl "{{.Values.foo}}" $context
     * Returns empty string on error
     */
    private static Function tpl(GoTemplate factory) {
        return args -> {
            if (args.length < 2) return "";
            String text = String.valueOf(args[0]);
            Object data = args[1];
            try {
                // Create a new template instance inheriting functions and named templates
                GoTemplate tplTemplate = new GoTemplate(factory.getFunctions());
                tplTemplate.getRootNodes().putAll(factory.getRootNodes());

                // Parse the inline template
                tplTemplate.parse("inline", text);

                // Execute and return result
                StringWriter writer = new StringWriter();
                tplTemplate.execute("inline", data, writer);
                return writer.toString();
            } catch (Exception e) {
                // Log warning but return empty string for compatibility
                return "";
            }
        };
    }

    /**
     * mustTpl evaluates a string as a template inline
     * Syntax: mustTpl "{{.Values.foo}}" $context
     * Throws exception on error
     */
    private static Function mustTpl(GoTemplate factory) {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("mustTpl: insufficient arguments (requires template string and context)");
            }
            String text = String.valueOf(args[0]);
            Object data = args[1];
            try {
                // Create a new template instance inheriting functions and named templates
                GoTemplate tplTemplate = new GoTemplate(factory.getFunctions());
                tplTemplate.getRootNodes().putAll(factory.getRootNodes());

                // Parse the inline template
                tplTemplate.parse("inline", text);

                // Execute and return result
                StringWriter writer = new StringWriter();
                tplTemplate.execute("inline", data, writer);
                return writer.toString();
            } catch (Exception e) {
                throw new RuntimeException("mustTpl: failed to evaluate template: " + e.getMessage(), e);
            }
        };
    }

    /**
     * required validates that a value is present
     * Syntax: required "error message" .Values.foo
     * Throws exception if value is empty/null
     */
    private static Function required() {
        return args -> {
            if (args.length < 2) {
                throw new RuntimeException("required: insufficient arguments");
            }
            String message = String.valueOf(args[0]);
            Object value = args[1];

            // Check if value is "empty" (null, empty string, false, empty collection)
            if (value == null ||
                    (value instanceof String && ((String) value).isEmpty()) ||
                    (value.equals(false)) ||
                    (value instanceof java.util.Collection && ((java.util.Collection<?>) value).isEmpty()) ||
                    (value instanceof java.util.Map && ((java.util.Map<?, ?>) value).isEmpty())) {
                throw new RuntimeException(message);
            }

            return value;
        };
    }
}
