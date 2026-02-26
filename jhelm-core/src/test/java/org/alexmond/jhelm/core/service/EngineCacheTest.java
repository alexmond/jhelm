package org.alexmond.jhelm.core.service;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.cache.TemplateCache;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineCacheTest {

	private static final Map<String, Object> RELEASE_INFO = Map.of("Name", "test-release", "Namespace", "default",
			"IsInstall", true, "IsUpgrade", false, "Revision", 1);

	private ChartLoader chartLoader;

	@BeforeEach
	void setUp() {
		chartLoader = new ChartLoader();
	}

	private Chart loadMinimalChart() throws Exception {
		return chartLoader.load(new File("src/test/resources/test-charts/minimal"));
	}

	@Test
	void render_withCache_producesIdenticalOutput() throws Exception {
		Chart chart = loadMinimalChart();
		TemplateCache cache = new TemplateCache(64);
		Engine engineWithCache = new Engine(cache, null);
		Engine engineWithout = new Engine();

		String withCache = engineWithCache.render(chart, Map.of(), RELEASE_INFO);
		String withoutCache = engineWithout.render(chart, Map.of(), RELEASE_INFO);

		assertNotNull(withCache);
		assertNotNull(withoutCache);
		assertEquals(withoutCache, withCache);
	}

	@Test
	void render_twiceSameChart_cacheIsPopulated() throws Exception {
		Chart chart = loadMinimalChart();
		TemplateCache cache = new TemplateCache(64);
		Engine engine = new Engine(cache, null);

		engine.render(chart, Map.of(), RELEASE_INFO);
		assertTrue(cache.size() > 0, "Cache should be populated after first render");

		int sizeAfterFirst = cache.size();
		engine.render(chart, Map.of(), RELEASE_INFO);
		// Cache size should not grow on second render (all hits)
		assertEquals(sizeAfterFirst, cache.size(), "Cache size should be stable after repeated renders");
	}

	@Test
	void render_contentChange_doesNotReturnStaleResult() throws Exception {
		TemplateCache cache = new TemplateCache(64);
		Engine engine = new Engine(cache, null);

		ChartMetadata metadata = ChartMetadata.builder().name("dynamic").version("1.0.0").build();

		Chart chartV1 = Chart.builder()
			.metadata(metadata)
			.templates(List.of(Chart.Template.builder()
				.name("configmap.yaml")
				.data("apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: v1\n")
				.build()))
			.build();

		String outputV1 = engine.render(chartV1, Map.of(), RELEASE_INFO);
		assertTrue(outputV1.contains("name: v1"), "First render should contain v1 content");

		// Different content → different hash → cache miss → fresh parse
		Chart chartV2 = Chart.builder()
			.metadata(metadata)
			.templates(List.of(Chart.Template.builder()
				.name("configmap.yaml")
				.data("apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: v2\n")
				.build()))
			.build();

		String outputV2 = engine.render(chartV2, Map.of(), RELEASE_INFO);
		assertTrue(outputV2.contains("name: v2"), "Second render should contain v2 content, not stale v1");
	}

}
