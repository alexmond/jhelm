package org.alexmond.jhelm.core.action;

import org.alexmond.jhelm.core.service.ChartLoader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ReleaseContext;
import org.alexmond.jhelm.core.service.Engine;

class TemplateActionTest {

	@Mock
	private Engine engine;

	private TemplateAction templateAction;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		templateAction = new TemplateAction(engine, new ChartLoader());
	}

	@Test
	void testRenderChart() throws Exception {
		// Create a minimal chart
		Path chartDir = tempDir.resolve("mychart");
		Files.createDirectories(chartDir);

		String chartYaml = """
				apiVersion: v2
				name: mychart
				version: 1.0.0
				""";
		Files.writeString(chartDir.resolve("Chart.yaml"), chartYaml);

		String manifest = "---\nkind: Service\nmetadata:\n  name: myservice";
		when(engine.render(any(Chart.class), anyMap(), any(ReleaseContext.class), any(Capabilities.class)))
			.thenReturn(manifest);

		String result = templateAction.render(chartDir.toString(), "myrelease", "default");

		assertNotNull(result);
		assertTrue(result.contains("Service"));
	}

	@Test
	void testRenderPassesCapabilitiesOverride() throws Exception {
		Path chartDir = tempDir.resolve("capchart");
		Files.createDirectories(chartDir);
		Files.writeString(chartDir.resolve("Chart.yaml"), """
				apiVersion: v2
				name: capchart
				version: 1.0.0
				""");

		ArgumentCaptor<Capabilities> capsCaptor = ArgumentCaptor.forClass(Capabilities.class);
		when(engine.render(any(Chart.class), anyMap(), any(ReleaseContext.class), capsCaptor.capture()))
			.thenReturn("---\n");

		templateAction.render(chartDir.toString(), "r", "default", new HashMap<>(), "v1.29.0", List.of("custom.io/v1"));

		Capabilities passed = capsCaptor.getValue();
		assertEquals("v1.29.0", passed.kubeVersion());
		assertTrue(passed.extraApiVersions().contains("custom.io/v1"));
	}

}
