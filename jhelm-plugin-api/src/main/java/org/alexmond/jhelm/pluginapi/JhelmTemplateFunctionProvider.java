package org.alexmond.jhelm.pluginapi;

import java.util.Map;

/**
 * A plugin that contributes Go-template functions usable in chart templates. jhelm
 * registers the returned functions with the template engine before rendering, so a chart
 * can call them like any built-in, Sprig, or Helm function.
 *
 * <p>
 * Function names should be namespaced or distinctive: a name that collides with a
 * built-in (Sprig/Helm) function <em>overrides</em> it, so an accidental clash can change
 * chart rendering. The engine's cluster-backed {@code lookup} is the one exception — it
 * is always preserved.
 */
public interface JhelmTemplateFunctionProvider extends JhelmPlugin {

	/**
	 * The functions this plugin contributes, keyed by the name templates call them by.
	 * @return an immutable map of function name to implementation (never {@code null})
	 */
	Map<String, JhelmTemplateFunction> functions();

}
