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
 * Regression test for #18: when an umbrella chart and one of its subcharts share the same
 * name, their template files collide on the {@code <name>/templates/<file>} key. The
 * version-based de-duplication used to drop the losing file entirely, losing its named
 * templates — so a {@code define} that lived only in the subchart's helper file (e.g.
 * gitlab's {@code gitlab.registry.hostname}) was reported "not found". Distinct-content
 * collisions must keep both files' defines.
 */
class NameCollisionTemplateTest {

	private final ChartLoader chartLoader = new ChartLoader();

	private final Engine engine = new Engine();

	private final InstallAction installAction = new InstallAction(engine, null);

	@Test
	void testSubchartOnlyDefineIsRegisteredDespiteNameCollision() throws Exception {
		File chartDir = new File("src/test/resources/test-charts/name-collision");
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

		// The umbrella's own helper resolves...
		assertTrue(manifest.contains("parent: parent-value"),
				"parentonly define (umbrella helper) should render; got:\n" + manifest);
		// ...and so does the define that exists ONLY in the like-named subchart's helper.
		assertTrue(manifest.contains("child: child-value"),
				"childonly define (subchart helper colliding on the same key) should render; got:\n" + manifest);
	}

}
