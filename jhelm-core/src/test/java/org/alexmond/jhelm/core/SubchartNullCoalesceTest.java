package org.alexmond.jhelm.core;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for jhelm #491 (cf. helm/helm#31919): a {@code --values} null for a subchart
 * key that the subchart does <em>not</em> declare in its own defaults must be kept (the
 * key stays present with a null value), not erased. Helm's coalesce only deletes a null
 * override when the chart's own {@code c.Values} declares that key; a standalone null is
 * never visited and is retained. (The complementary "null deletes the subchart's own
 * default" case is covered by
 * {@code SubchartValuePropagationTest#testParentNullOverrideDeletesSubchartDefault}.)
 */
class SubchartNullCoalesceTest {

	private final ChartLoader chartLoader = new ChartLoader();

	private final InstallAction installAction = new InstallAction(new Engine(), null);

	@Test
	void nullOverrideOnUndeclaredSubchartKeyIsKept() throws Exception {
		Chart chart = chartLoader.load(new File("src/test/resources/test-charts/subchart-null-keep"));
		assertNotNull(chart);

		// The child subchart declares no `foo`; the parent sets child.foo=bar, and the
		// user
		// overrides it to null. Helm keeps the key (hasKey == true); jhelm previously
		// pruned
		// it (hasKey == false).
		Map<String, Object> childOverride = new HashMap<>();
		childOverride.put("foo", null);
		Map<String, Object> overrides = Map.of("child", childOverride);

		Release release = installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("rel")
			.namespace("default")
			.values(overrides)
			.revision(1)
			.dryRun(true)
			.build());
		String manifest = release.getManifest();
		assertTrue(manifest.contains("haskey: \"true\""),
				"a null override of an undeclared subchart key must keep the key (hasKey true): " + manifest);
	}

}
