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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rendered manifest carries a Helm-style {@code # Source: <chart>/templates/<file>}
 * marker before each document, matching {@code helm template} output. These markers
 * record which template produced each document and enable {@code --show-only} /
 * {@code --output-dir}.
 */
class TemplateSourceMarkerTest {

	private final ChartLoader chartLoader = new ChartLoader();

	private final Engine engine = new Engine();

	private final InstallAction installAction = new InstallAction(engine, null);

	private String render(String chartName) throws Exception {
		Chart chart = chartLoader.load(new File("src/test/resources/test-charts/" + chartName));
		Release release = installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("test-release")
			.namespace("default")
			.values(Map.of())
			.revision(1)
			.dryRun(true)
			.build());
		return release.getManifest();
	}

	@Test
	void testSourceMarkerEmittedForTemplate() throws Exception {
		String manifest = render("minimal");
		assertTrue(manifest.contains("# Source: minimal/templates/configmap.yaml"),
				"expected Helm-style source marker; got:\n" + manifest);
	}

	@Test
	void testSourceMarkerPrecedesTheDocumentContent() throws Exception {
		String manifest = render("minimal");
		int marker = manifest.indexOf("# Source: minimal/templates/configmap.yaml");
		int kind = manifest.indexOf("kind: ConfigMap");
		assertTrue(marker >= 0 && kind > marker,
				"source marker must precede the document it labels; got:\n" + manifest);
	}

}
