package org.alexmond.jhelm.core;

import java.io.File;
import java.util.Map;

import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for #21: when the same named template is defined by distinct subcharts
 * (here the umbrella's {@code templates/_helpers.tpl} and a {@code zzz} subchart's), Helm
 * parses templates in full-path order — {@code .../charts/zzz/templates/...} sorts before
 * {@code .../templates/...} — so the umbrella's definition is parsed last and wins. jhelm
 * must resolve the same winner (verified against {@code helm template}: {@code
 * from-umbrella}), not the one that merely sorts last by chart name.
 */
class DefineOrderTest {

	private final ChartLoader chartLoader = new ChartLoader();

	private final Engine engine = new Engine();

	private final InstallAction installAction = new InstallAction(engine, null);

	@Test
	void testFullPathOrderingPicksSameDefineWinnerAsHelm() throws Exception {
		File chartDir = new File("src/test/resources/test-charts/define-order");
		Chart chart = chartLoader.load(chartDir);
		assertNotNull(chart, "Chart should be loaded");

		Release release = installAction.install(chart, "test-release", "default", Map.of(), 1, true);
		assertNotNull(release, "Release should be created");
		String manifest = release.getManifest();

		assertTrue(manifest.contains("value: from-umbrella"),
				"umbrella define must win via Helm full-path parse order; got:\n" + manifest);
	}

}
