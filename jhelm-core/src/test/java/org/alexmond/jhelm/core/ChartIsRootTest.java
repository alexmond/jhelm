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
 * Helm's {@code .Chart.IsRoot} is true only for the chart being installed (the top-level
 * release chart) and false when the same template runs as a subchart. The test chart
 * emits {@code .Chart.IsRoot} from both the parent and a subchart ConfigMap.
 */
class ChartIsRootTest {

	@Test
	void testIsRootTrueForParentFalseForSubchart() throws Exception {
		ChartLoader chartLoader = new ChartLoader();
		Engine engine = new Engine();
		InstallAction installAction = new InstallAction(engine, null);

		Chart chart = chartLoader.load(new File("src/test/resources/test-charts/chart-isroot"));
		assertNotNull(chart, "Chart should be loaded");
		Release release = installAction.install(chart, "r", "default", Map.of(), 1, true);
		String manifest = release.getManifest();

		// Parent ConfigMap: the release chart is root.
		assertTrue(manifest.contains("name: parent") && manifest.contains("isRoot: \"true\""),
				"parent .Chart.IsRoot must be true: " + manifest);
		// Subchart ConfigMap: rendered as a subchart, so not root.
		assertTrue(manifest.contains("name: sub") && manifest.contains("isRoot: \"false\""),
				"subchart .Chart.IsRoot must be false: " + manifest);
	}

}
