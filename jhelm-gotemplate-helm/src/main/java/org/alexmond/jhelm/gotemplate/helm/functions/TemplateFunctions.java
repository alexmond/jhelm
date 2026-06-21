package org.alexmond.jhelm.gotemplate.helm.functions;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.alexmond.gotmpl4j.Function;
import org.alexmond.gotmpl4j.GoTemplate;
import org.alexmond.gotmpl4j.FunctionExecutionException;

/**
 * Helm template-specific functions for template inclusion and evaluation Based on:
 * <a href=
 * "https://helm.sh/docs/chart_template_guide/function_list/">https://helm.sh/docs/chart_template_guide/function_list/</a>
 */
public final class TemplateFunctions {

	private TemplateFunctions() {
	}

	/**
	 * Get template functions that require access to the GoTemplate
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
	 * include executes a named template and returns its output as a string. Syntax:
	 * include "templateName" $context. Propagates errors as
	 * {@link FunctionExecutionException}, matching Go Helm behavior.
	 */
	private static Function include(GoTemplate factory) {
		return (args) -> {
			if (args.length < 2) {
				return "";
			}
			String name = String.valueOf(args[0]);
			Object data = args[1];
			try {
				StringWriter writer = new StringWriter();
				factory.execute(name, data, writer);
				return writer.toString();
			}
			catch (Exception ex) {
				throw new FunctionExecutionException(
						"include: failed to execute template '" + name + "': " + ex.getMessage(), ex);
			}
		};
	}

	/**
	 * mustInclude executes a named template and returns its output as a string Syntax:
	 * mustInclude "templateName" $context Throws exception on error
	 */
	private static Function mustInclude(GoTemplate factory) {
		return (args) -> {
			if (args.length < 2) {
				throw new FunctionExecutionException(
						"mustInclude: insufficient arguments (requires template name and context)");
			}
			String name = String.valueOf(args[0]);
			Object data = args[1];
			try {
				StringWriter writer = new StringWriter();
				factory.execute(name, data, writer);
				return writer.toString();
			}
			catch (Exception ex) {
				throw new FunctionExecutionException(
						"mustInclude: failed to execute template '" + name + "': " + ex.getMessage(), ex);
			}
		};
	}

	/**
	 * tpl evaluates a string as a template inline. Syntax: tpl "{{.Values.foo}}"
	 * $context. Propagates errors as {@link FunctionExecutionException}, matching Go Helm
	 * behavior.
	 */
	private static Function tpl(GoTemplate factory) {
		return (args) -> {
			if (args.length < 2) {
				return "";
			}
			String text = String.valueOf(args[0]);
			Object data = args[1];
			try {
				return renderInline(factory, text, data);
			}
			catch (Exception ex) {
				throw new FunctionExecutionException("tpl: failed to evaluate template: " + ex.getMessage(), ex);
			}
		};
	}

	/**
	 * Parse and execute an inline template string against the engine's shared template
	 * set. Named templates ({@code define} blocks) declared inside the inline text are
	 * registered back into {@code factory} so that {@code include}/{@code template} —
	 * which resolve against {@code factory} — can find them. This matches Helm, where a
	 * template defined within a {@code tpl} string joins the global namespace (e.g. a
	 * chart that declares {@code define} and {@code include} of the same name inside a
	 * single values string).
	 * @param factory the engine template set whose functions and named templates are
	 * inherited
	 * @param text the inline template source
	 * @param data the rendering context
	 * @return the rendered output
	 */
	private static String renderInline(GoTemplate factory, String text, Object data) throws Exception {
		GoTemplate tplTemplate = new GoTemplate(factory.getFunctions());
		tplTemplate.getRootNodes().putAll(factory.getRootNodes());
		tplTemplate.parse("inline", text);
		for (var entry : tplTemplate.getRootNodes().entrySet()) {
			if (!"inline".equals(entry.getKey())) {
				factory.getRootNodes().putIfAbsent(entry.getKey(), entry.getValue());
			}
		}
		StringWriter writer = new StringWriter();
		tplTemplate.execute("inline", data, writer);
		return writer.toString();
	}

	/**
	 * mustTpl evaluates a string as a template inline Syntax: mustTpl "{{.Values.foo}}"
	 * $context Throws exception on error
	 */
	private static Function mustTpl(GoTemplate factory) {
		return (args) -> {
			if (args.length < 2) {
				throw new FunctionExecutionException(
						"mustTpl: insufficient arguments (requires template string and context)");
			}
			String text = String.valueOf(args[0]);
			Object data = args[1];
			try {
				return renderInline(factory, text, data);
			}
			catch (Exception ex) {
				throw new FunctionExecutionException("mustTpl: failed to evaluate template: " + ex.getMessage(), ex);
			}
		};
	}

	/**
	 * required validates that a value is present Syntax: required "error message"
	 * .Values.foo Throws exception if value is empty/null
	 */
	private static Function required() {
		return (args) -> {
			if (args.length < 2) {
				throw new FunctionExecutionException("required: insufficient arguments");
			}
			String message = String.valueOf(args[0]);
			Object value = args[1];

			// Helm's `required` rejects only nil and the empty string — a boolean false,
			// an empty list, or an empty map are all valid values and must pass through
			// (e.g. matrix-synapse's `required "..." .Values.config.reportStats` with
			// reportStats: false).
			if (value == null || (value instanceof String && ((String) value).isEmpty())) {
				throw new FunctionExecutionException(message);
			}

			return value;
		};
	}

}
