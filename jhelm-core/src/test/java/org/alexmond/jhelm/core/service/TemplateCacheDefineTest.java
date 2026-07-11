package org.alexmond.jhelm.core.service;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.alexmond.jhelm.core.cache.TemplateCache;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ReleaseContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for #726: when a {@link TemplateCache} is shared across renders of distinct
 * charts, a library subchart's {@code define}d template (keyed by its subchart-relative
 * path, so shared across parents) must still resolve. Chart A defines {@code lib.full} in
 * an earlier-parsed subchart before {@code lib}'s helper parses, so {@code lib}'s cached
 * node delta omits the define; chart B then reuses that partial entry and must not lose
 * the define.
 */
class TemplateCacheDefineTest {

	private static final Path CHARTS = Path.of("src/test/resources/test-charts");

	@Test
	void libraryDefineSurvivesSharedCacheAcrossCharts() {
		TemplateCache cache = new TemplateCache(256);
		Engine engine = new Engine(cache, new SchemaValidator());
		ReleaseContext release = ReleaseContext.builder().name("r").namespace("default").build();

		ChartLoader loader = new ChartLoader();
		Chart chartA = loader.load(CHARTS.resolve("cache-define-a").toFile());
		Chart chartB = loader.load(CHARTS.resolve("cache-define-b").toFile());

		// Render A first: populates the cache with a partial delta for
		// lib/templates/_helpers.tpl
		String a = engine.render(chartA, Map.of(), release);
		assertTrue(a.contains("full:"), "chart A should render the include: " + a);

		// Render B through the SAME cache: the lib define must still resolve, not "not
		// found".
		String b = engine.render(chartB, Map.of(), release);
		assertTrue(b.contains("full: lib-owner"),
				"chart B lost the library define through the shared cache (#726): " + b);
	}

}
