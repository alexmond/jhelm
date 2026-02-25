package org.alexmond.jhelm.core.action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import org.alexmond.jhelm.core.model.Chart;
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
		templateAction = new TemplateAction(engine);
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
		when(engine.render(any(Chart.class), anyMap(), anyMap())).thenReturn(manifest);

		String result = templateAction.render(chartDir.toString(), "myrelease", "default");

		assertNotNull(result);
		assertTrue(result.contains("Service"));
	}

}
