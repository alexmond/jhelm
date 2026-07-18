package org.alexmond.jhelm.core.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.alexmond.gotmpl4j.Function;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.ReleaseContext;
import org.alexmond.jhelm.pluginapi.JhelmPluginException;
import org.alexmond.jhelm.pluginapi.JhelmTemplateFunction;
import org.alexmond.jhelm.pluginapi.JhelmTemplateFunctionProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JhelmTemplateFunctionAdapterTest {

	@Test
	void adaptInvokesThePluginFunction() {
		Function fn = JhelmTemplateFunctionAdapter.adapt("shout",
				(args) -> ((String) args[0]).toUpperCase(Locale.ROOT));
		assertEquals("HI", fn.invoke("hi"));
	}

	@Test
	void adaptTranslatesPluginExceptionToUnchecked() {
		JhelmTemplateFunction failing = (args) -> {
			throw new JhelmPluginException("bad args");
		};
		Function fn = JhelmTemplateFunctionAdapter.adapt("boom", failing);
		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> fn.invoke());
		assertTrue(ex.getMessage().contains("bad args"));
	}

	@Test
	void collectGathersFunctionsFromProviders() {
		JhelmTemplateFunctionProvider provider = () -> Map.of("greet",
				(JhelmTemplateFunction) (args) -> "hello " + args[0]);
		Map<String, Function> functions = JhelmTemplateFunctionAdapter.collect(List.of(provider));
		assertEquals("hello world", functions.get("greet").invoke("world"));
	}

	@Test
	void pluginFunctionIsCallableFromAChartTemplate() {
		Engine engine = new Engine();
		engine.setPluginFunctions(JhelmTemplateFunctionAdapter.collect(List
			.of(() -> Map.of("shout", (JhelmTemplateFunction) (args) -> ((String) args[0]).toUpperCase(Locale.ROOT)))));
		Chart chart = Chart.builder()
			.metadata(ChartMetadata.builder().name("mychart").version("1.0.0").build())
			.templates(List.of(Chart.Template.builder().name("cm.yaml").data("name: {{ shout \"hi\" }}").build()))
			.values(Map.of())
			.build();

		String result = engine.render(chart, Map.of(),
				ReleaseContext.builder().name("rel").namespace("default").install(true).revision(1).build());

		assertTrue(result.contains("name: HI"), result);
	}

}
