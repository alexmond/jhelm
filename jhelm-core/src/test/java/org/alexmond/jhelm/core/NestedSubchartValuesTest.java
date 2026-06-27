package org.alexmond.jhelm.core;

import java.io.File;
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
 * Regression test for #21: a default declared by a sub-subchart must be visible to the
 * umbrella at {@code .Values.<mid>.<leaf>.*}. Helm coalesces subchart values recursively
 * (its {@code chartutil.CoalesceValues}), so the umbrella sees the whole nested default
 * tree — e.g. gitlab reading {@code .Values.gitlab.gitlab-shell.enabled}. Verified
 * against {@code helm template} ({@code leafColor: blue}).
 */
class NestedSubchartValuesTest {

	private final ChartLoader chartLoader = new ChartLoader();

	private final Engine engine = new Engine();

	private final InstallAction installAction = new InstallAction(engine, null);

	@Test
	void testTwoLevelDeepSubchartDefaultIsVisibleToUmbrella() throws Exception {
		File chartDir = new File("src/test/resources/test-charts/nested-values");
		Chart chart = chartLoader.load(chartDir);
		assertNotNull(chart, "Chart should be loaded");

		Release release = installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("test-release")
			.namespace("default")
			.values(Map.of())
			.revision(1)
			.dryRun(true)
			.build());
		assertNotNull(release, "Release should be created");
		String manifest = release.getManifest();

		assertTrue(manifest.contains("leafColor: blue"),
				"umbrella must see the leaf sub-subchart default via recursive coalescing; got:\n" + manifest);
	}

}
