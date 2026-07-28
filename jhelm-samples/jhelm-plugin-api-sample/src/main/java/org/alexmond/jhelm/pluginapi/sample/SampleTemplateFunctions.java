package org.alexmond.jhelm.pluginapi.sample;

import java.util.Locale;
import java.util.Map;

import org.alexmond.jhelm.pluginapi.JhelmTemplateFunction;
import org.alexmond.jhelm.pluginapi.JhelmTemplateFunctionProvider;

/**
 * Sample {@link JhelmTemplateFunctionProvider} contributing a couple of namespaced
 * template functions ({@code sample_greet}, {@code sample_shout}) callable from any
 * chart. Names are prefixed to avoid clashing with built-in functions.
 */
public class SampleTemplateFunctions implements JhelmTemplateFunctionProvider {

	@Override
	public String name() {
		return "sample-functions";
	}

	@Override
	public Map<String, JhelmTemplateFunction> functions() {
		return Map.of("sample_greet", (args) -> "hello, " + args[0], "sample_shout",
				(args) -> String.valueOf(args[0]).toUpperCase(Locale.ROOT));
	}

}
