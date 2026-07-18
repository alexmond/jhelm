package org.alexmond.jhelm.pluginapi;

/**
 * A single Go-template function contributed by a {@link JhelmTemplateFunctionProvider}.
 * Invoked from a chart template as {@code {{ myFunc arg1 arg2 }}}; the arguments arrive
 * as the values the template engine resolved, and the return value is rendered.
 */
@FunctionalInterface
public interface JhelmTemplateFunction {

	/**
	 * Applies the function to the template-supplied arguments.
	 * @param args the arguments passed in the template call (may be empty)
	 * @return the result to render
	 * @throws JhelmPluginException if the function fails (aborts rendering)
	 */
	Object apply(Object... args) throws JhelmPluginException;

}
