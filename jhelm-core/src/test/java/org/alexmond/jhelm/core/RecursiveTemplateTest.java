package org.alexmond.jhelm.core;

import java.io.File;
import java.util.Map;

import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.exception.TemplateRenderException;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression test for #515: a chart whose template recurses without bound must fail
 * loudly with a {@link TemplateRenderException}, not "succeed" with an {@code ERROR:}
 * string baked into the rendered manifest (which could otherwise reach a cluster).
 */
class RecursiveTemplateTest {

	private final ChartLoader chartLoader = new ChartLoader();

	private final Engine engine = new Engine();

	private final InstallAction installAction = new InstallAction(engine, null);

	@Test
	void testRecursiveTemplateFailsLoudly() throws Exception {
		File chartDir = new File("src/test/resources/test-charts/recursive-template");
		Chart chart = chartLoader.load(chartDir);
		assertThrows(TemplateRenderException.class,
				() -> installAction.install(InstallOptions.builder()
					.chart(chart)
					.releaseName("rec")
					.namespace("default")
					.values(Map.of())
					.revision(1)
					.dryRun(true)
					.build()));
	}

}
