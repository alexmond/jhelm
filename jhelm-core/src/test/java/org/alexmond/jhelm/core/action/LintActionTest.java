package org.alexmond.jhelm.core.action;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.SchemaValidator;

class LintActionTest {

	@TempDir
	Path tempDir;

	private LintAction lintAction;

	@BeforeEach
	void setUp() {
		ChartLoader chartLoader = new ChartLoader();
		Engine engine = new Engine(null, new SchemaValidator(), null);
		SchemaValidator schemaValidator = new SchemaValidator();
		lintAction = new LintAction(chartLoader, engine, schemaValidator);
	}

	@Test
	void testLintValidChart() throws Exception {
		Path chartDir = createMinimalChart("good-chart", "1.0.0", "v2");
		LintAction.LintResult result = lintAction.lint(chartDir.toString(), null, false);
		assertTrue(result.isOk(), "valid chart should pass lint: " + result.getErrors());
	}

	@Test
	void testLintMissingChartYaml() {
		Path emptyDir = tempDir.resolve("empty");
		emptyDir.toFile().mkdirs();
		LintAction.LintResult result = lintAction.lint(emptyDir.toString(), null, false);
		assertFalse(result.isOk());
		assertTrue(result.getErrors().stream().anyMatch((e) -> e.contains("failed to load chart")));
	}

	@Test
	void testLintNonExistentPath() {
		LintAction.LintResult result = lintAction.lint("/nonexistent/path", null, false);
		assertFalse(result.isOk());
		assertTrue(result.getErrors().stream().anyMatch((e) -> e.contains("does not exist")));
	}

	@Test
	void testLintMissingName() throws Exception {
		Path chartDir = tempDir.resolve("no-name");
		Files.createDirectories(chartDir);
		Files.writeString(chartDir.resolve("Chart.yaml"), """
				apiVersion: v2
				version: 1.0.0
				description: missing name
				""");
		LintAction.LintResult result = lintAction.lint(chartDir.toString(), null, false);
		assertFalse(result.isOk());
		assertTrue(result.getErrors().stream().anyMatch((e) -> e.contains("chart name is required")));
	}

	@Test
	void testLintMissingDescription() throws Exception {
		Path chartDir = createChart("no-desc", "1.0.0", "v2", null);
		LintAction.LintResult result = lintAction.lint(chartDir.toString(), null, false);
		assertTrue(result.isOk());
		assertTrue(result.getWarnings().stream().anyMatch((w) -> w.contains("missing a description")));
	}

	@Test
	void testLintSchemaViolation() throws Exception {
		Path chartDir = createMinimalChart("schema-chart", "1.0.0", "v2");
		Files.writeString(chartDir.resolve("values.yaml"), "replicaCount: not-a-number\n");
		Files.writeString(chartDir.resolve("values.schema.json"), """
				{
				  "type": "object",
				  "properties": {
				    "replicaCount": { "type": "integer" }
				  }
				}
				""");
		LintAction.LintResult result = lintAction.lint(chartDir.toString(), null, false);
		assertFalse(result.isOk());
		assertTrue(result.getErrors().stream().anyMatch((e) -> e.contains("schema validation")));
	}

	@Test
	void testLintTemplateSyntaxError() throws Exception {
		Path chartDir = createMinimalChart("bad-template", "1.0.0", "v2");
		Files.createDirectories(chartDir.resolve("templates"));
		Files.writeString(chartDir.resolve("templates/broken.yaml"), "{{ .Values.missing }");
		LintAction.LintResult result = lintAction.lint(chartDir.toString(), null, false);
		assertFalse(result.isOk());
		assertTrue(result.getErrors().stream().anyMatch((e) -> e.contains("template rendering failed")));
	}

	@Test
	void testLintWithOverrideValues() throws Exception {
		Path chartDir = createMinimalChart("override-chart", "1.0.0", "v2");
		Files.writeString(chartDir.resolve("values.yaml"), "replicaCount: 1\n");
		Files.writeString(chartDir.resolve("values.schema.json"), """
				{
				  "type": "object",
				  "properties": {
				    "replicaCount": { "type": "integer" }
				  }
				}
				""");
		LintAction.LintResult result = lintAction.lint(chartDir.toString(), Map.of("replicaCount", 3), false);
		assertTrue(result.isOk(), "valid override values should pass: " + result.getErrors());
	}

	private Path createMinimalChart(String name, String version, String apiVersion) throws Exception {
		return createChart(name, version, apiVersion, "A test chart");
	}

	private Path createChart(String name, String version, String apiVersion, String description) throws Exception {
		Path chartDir = tempDir.resolve(name);
		Files.createDirectories(chartDir);
		StringBuilder yaml = new StringBuilder();
		yaml.append("apiVersion: ").append(apiVersion).append('\n');
		yaml.append("name: ").append(name).append('\n');
		yaml.append("version: ").append(version).append('\n');
		if (description != null) {
			yaml.append("description: ").append(description).append('\n');
		}
		Files.writeString(chartDir.resolve("Chart.yaml"), yaml.toString());
		Files.writeString(chartDir.resolve("values.yaml"), "replicaCount: 1\n");
		return chartDir;
	}

}
