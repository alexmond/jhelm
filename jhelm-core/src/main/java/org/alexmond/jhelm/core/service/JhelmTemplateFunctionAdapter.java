package org.alexmond.jhelm.core.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.gotmpl4j.Function;
import org.alexmond.jhelm.pluginapi.JhelmPluginException;
import org.alexmond.jhelm.pluginapi.JhelmTemplateFunction;
import org.alexmond.jhelm.pluginapi.JhelmTemplateFunctionProvider;

/**
 * Bridges Java {@link JhelmTemplateFunctionProvider} plugins to gotmpl4j
 * {@link Function}s the render engine can register. A {@link JhelmPluginException} from a
 * plugin function surfaces as an unchecked exception that aborts rendering.
 */
public final class JhelmTemplateFunctionAdapter {

	private JhelmTemplateFunctionAdapter() {
	}

	/**
	 * Collects the functions contributed by the given providers into a single map,
	 * adapting each to a gotmpl4j {@link Function}. Later providers override earlier ones
	 * on a name clash.
	 * @param providers the template-function plugins
	 * @return the adapted functions keyed by template name
	 */
	public static Map<String, Function> collect(List<JhelmTemplateFunctionProvider> providers) {
		Map<String, Function> functions = new LinkedHashMap<>();
		for (JhelmTemplateFunctionProvider provider : providers) {
			provider.functions().forEach((name, function) -> functions.put(name, adapt(name, function)));
		}
		return functions;
	}

	/**
	 * Adapts a single {@link JhelmTemplateFunction} to a gotmpl4j {@link Function}.
	 * @param name the function name (for error messages)
	 * @param function the plugin function
	 * @return the adapted gotmpl4j function
	 */
	public static Function adapt(String name, JhelmTemplateFunction function) {
		return (args) -> {
			try {
				return function.apply(args);
			}
			catch (JhelmPluginException ex) {
				throw new IllegalStateException("template function '" + name + "' failed: " + ex.getMessage(), ex);
			}
		};
	}

}
