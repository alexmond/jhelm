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

	@Test
	void testLintWithSubchartsSurfacesSubchartFindings() throws Exception {
		Path parent = createChart("parent-chart", "1.0.0", "v2", "Parent chart");
		Path subDir = parent.resolve("charts/badsub");
		Files.createDirectories(subDir);
		// Subchart missing a description -> a lint warning.
		Files.writeString(subDir.resolve("Chart.yaml"), "apiVersion: v2\nname: badsub\nversion: 0.1.0\n");
		Files.writeString(subDir.resolve("values.yaml"), "{}\n");

		// Without --with-subcharts the subchart is not inspected.
		LintAction.LintResult without = lintAction.lint(parent.toString(), null, false, false, null);
		assertFalse(without.getWarnings().stream().anyMatch((w) -> w.contains("[badsub]")),
				without.getWarnings().toString());

		// With --with-subcharts the subchart's warning surfaces, prefixed by its name.
		LintAction.LintResult with = lintAction.lint(parent.toString(), null, false, true, null);
		assertTrue(with.getWarnings().stream().anyMatch((w) -> w.contains("[badsub]")), with.getWarnings().toString());
	}

	@Test
	void testLintWithKubeVersionRendersTemplates() throws Exception {
		Path chartDir = createMinimalChart("kubever-chart", "1.0.0", "v2");
		Files.createDirectories(chartDir.resolve("templates"));
		// A template that reads .Capabilities.KubeVersion must render without error.
		Files.writeString(chartDir.resolve("templates/cm.yaml"), """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: cm
				data:
				  kube: "{{ .Capabilities.KubeVersion.Version }}"
				""");
		LintAction.LintResult result = lintAction.lint(chartDir.toString(), null, false, false, "v1.30.0");
		assertTrue(result.isOk(), "rendering with an explicit kube-version should pass: " + result.getErrors());
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
