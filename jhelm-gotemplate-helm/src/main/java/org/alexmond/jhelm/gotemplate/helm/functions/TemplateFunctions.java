package org.alexmond.jhelm.gotemplate.helm.functions;

import java.io.IOException;
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

	// Reserved root-node key under which the tpl fast path parses an inline string in the
	// parent factory. Chosen so it can't collide with a chart's template path or define
	// name.
	private static final String TPL_INLINE_NAME = "__jhelm_tpl_inline__";

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
	private static String renderInline(GoTemplate factory, String text, Object data) throws IOException {
		StringWriter writer = new StringWriter();
		// Fast path: a tpl string that declares no `define` contributes only its own
		// body, so
		// parse and execute it directly in the parent factory under a reserved name. This
		// reuses the factory's already-built function registry and named templates
		// instead of
		// constructing a fresh GoTemplate per call — which re-runs ServiceLoader function
		// discovery and copies the entire root-node table on every tpl invocation. The
		// factory
		// is already missingkey=zero (set per render, the same option the slow path
		// applies),
		// the reserved name can't collide with a chart template/define, and it is
		// overwritten
		// on each call; execute() resolves its node at entry, so nested tpl calls stay
		// correct.
		if (!text.contains("define")) {
			factory.parse(TPL_INLINE_NAME, text);
			factory.execute(TPL_INLINE_NAME, data, writer);
			return writer.toString();
		}
		// Slow path: the tpl string declares templates — isolate the parse in a child so
		// its
		// defines merge into the parent without overwriting existing ones (putIfAbsent),
		// which
		// matches Helm where a template defined within a tpl string joins the global
		// namespace.
		GoTemplate tplTemplate = new GoTemplate(factory.getFunctions()).option("missingkey=zero");
		tplTemplate.getRootNodes().putAll(factory.getRootNodes());
		tplTemplate.parse("inline", text);
		for (var entry : tplTemplate.getRootNodes().entrySet()) {
			if (!"inline".equals(entry.getKey())) {
				factory.getRootNodes().putIfAbsent(entry.getKey(), entry.getValue());
			}
		}
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
