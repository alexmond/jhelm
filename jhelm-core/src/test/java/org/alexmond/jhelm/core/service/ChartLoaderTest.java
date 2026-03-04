package org.alexmond.jhelm.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.alexmond.jhelm.core.exception.ChartLoadException;
import org.alexmond.jhelm.core.model.Chart;

class ChartLoaderTest {

	private ChartLoader chartLoader;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		chartLoader = new ChartLoader();
	}

	@Test
	void testLoadChartWithReadme() throws Exception {
		// Create a minimal chart with README
		Path chartDir = tempDir.resolve("test-chart");
		Files.createDirectories(chartDir);

		// Create Chart.yaml
		String chartYaml = """
				apiVersion: v2
				name: test-chart
				version: 1.0.0
				description: A test chart
				""";
		Files.writeString(chartDir.resolve("Chart.yaml"), chartYaml);

		// Create README.md
		String readme = """
				# Test Chart

				This is a test chart for testing README loading.
				""";
		Files.writeString(chartDir.resolve("README.md"), readme);

		// Create values.yaml
		String values = """
				replicaCount: 1
				image:
				  repository: nginx
				  tag: latest
				""";
		Files.writeString(chartDir.resolve("values.yaml"), values);

		// Create templates directory
		Files.createDirectories(chartDir.resolve("templates"));

		// Load chart
		Chart chart = chartLoader.load(chartDir.toFile());

		// Verify chart loaded correctly
		assertNotNull(chart);
		assertNotNull(chart.getMetadata());
		assertEquals("test-chart", chart.getMetadata().getName());
		assertEquals("1.0.0", chart.getMetadata().getVersion());

		// Verify README loaded
		assertNotNull(chart.getReadme());
		assertTrue(chart.getReadme().contains("Test Chart"));
		assertTrue(chart.getReadme().contains("test chart for testing README loading"));
	}

	@Test
	void testLoadChartWithCrds() throws Exception {
		// Create a minimal chart with CRDs
		Path chartDir = tempDir.resolve("test-chart-crds");
		Files.createDirectories(chartDir);

		// Create Chart.yaml
		String chartYaml = """
				apiVersion: v2
				name: test-chart-crds
				version: 1.0.0
				description: A test chart with CRDs
				""";
		Files.writeString(chartDir.resolve("Chart.yaml"), chartYaml);

		// Create values.yaml
		Files.writeString(chartDir.resolve("values.yaml"), "{}");

		// Create templates directory
		Files.createDirectories(chartDir.resolve("templates"));

		// Create crds directory and files
		Path crdsDir = chartDir.resolve("crds");
		Files.createDirectories(crdsDir);

		String crd1 = """
				apiVersion: apiextensions.k8s.io/v1
				kind: CustomResourceDefinition
				metadata:
				  name: mycrd1.example.com
				spec:
				  group: example.com
				  names:
				    kind: MyCRD1
				    plural: mycrd1s
				  scope: Namespaced
				  versions:
				  - name: v1
				    served: true
				    storage: true
				""";
		Files.writeString(crdsDir.resolve("mycrd1.yaml"), crd1);

		String crd2 = """
				apiVersion: apiextensions.k8s.io/v1
				kind: CustomResourceDefinition
				metadata:
				  name: mycrd2.example.com
				spec:
				  group: example.com
				  names:
				    kind: MyCRD2
				    plural: mycrd2s
				  scope: Cluster
				  versions:
				  - name: v1
				    served: true
				    storage: true
				""";
		Files.writeString(crdsDir.resolve("mycrd2.yaml"), crd2);

		// Load chart
		Chart chart = chartLoader.load(chartDir.toFile());

		// Verify chart loaded correctly
		assertNotNull(chart);
		assertNotNull(chart.getMetadata());
		assertEquals("test-chart-crds", chart.getMetadata().getName());

		// Verify CRDs loaded
		assertNotNull(chart.getCrds());
		assertEquals(2, chart.getCrds().size());

		// Verify CRD content
		boolean foundCrd1 = false;
		boolean foundCrd2 = false;
		for (Chart.Crd crd : chart.getCrds()) {
			if (crd.getData().contains("MyCRD1")) {
				foundCrd1 = true;
			}
			if (crd.getData().contains("MyCRD2")) {
				foundCrd2 = true;
			}
		}
		assertTrue(foundCrd1, "CRD1 should be loaded");
		assertTrue(foundCrd2, "CRD2 should be loaded");
	}

	@Test
	void testLoadChartWithoutReadme() throws Exception {
		// Create a minimal chart without README
		Path chartDir = tempDir.resolve("test-chart-no-readme");
		Files.createDirectories(chartDir);

		// Create Chart.yaml
		String chartYaml = """
				apiVersion: v2
				name: test-chart-no-readme
				version: 1.0.0
				""";
		Files.writeString(chartDir.resolve("Chart.yaml"), chartYaml);

		// Create values.yaml
		Files.writeString(chartDir.resolve("values.yaml"), "{}");

		// Create templates directory
		Files.createDirectories(chartDir.resolve("templates"));

		// Load chart
		Chart chart = chartLoader.load(chartDir.toFile());

		// Verify chart loaded correctly
		assertNotNull(chart);

		// Verify README is null
		assertNull(chart.getReadme());
	}

	@Test
	void testLoadChartWithoutCrds() throws Exception {
		// Create a minimal chart without CRDs
		Path chartDir = tempDir.resolve("test-chart-no-crds");
		Files.createDirectories(chartDir);

		// Create Chart.yaml
		String chartYaml = """
				apiVersion: v2
				name: test-chart-no-crds
				version: 1.0.0
				""";
		Files.writeString(chartDir.resolve("Chart.yaml"), chartYaml);

		// Create values.yaml
		Files.writeString(chartDir.resolve("values.yaml"), "{}");

		// Create templates directory
		Files.createDirectories(chartDir.resolve("templates"));

		// Load chart
		Chart chart = chartLoader.load(chartDir.toFile());

		// Verify chart loaded correctly
		assertNotNull(chart);

		// Verify CRDs list is empty
		assertNotNull(chart.getCrds());
		assertTrue(chart.getCrds().isEmpty());
	}

	@Test
	void testLoadChartWithEverything() throws Exception {
		// Create a complete chart with README, CRDs, templates, and values
		Path chartDir = tempDir.resolve("complete-chart");
		Files.createDirectories(chartDir);

		// Create Chart.yaml
		String chartYaml = """
				apiVersion: v2
				name: complete-chart
				version: 2.0.0
				description: A complete test chart
				appVersion: "1.0"
				type: application
				""";
		Files.writeString(chartDir.resolve("Chart.yaml"), chartYaml);

		// Create README.md
		String readme = """
				# Complete Chart

				This chart has everything.
				""";
		Files.writeString(chartDir.resolve("README.md"), readme);

		// Create values.yaml
		String values = """
				service:
				  type: ClusterIP
				  port: 80
				""";
		Files.writeString(chartDir.resolve("values.yaml"), values);

		// Create templates
		Path templatesDir = chartDir.resolve("templates");
		Files.createDirectories(templatesDir);
		Files.writeString(templatesDir.resolve("deployment.yaml"), "apiVersion: apps/v1\nkind: Deployment");

		// Create CRDs
		Path crdsDir = chartDir.resolve("crds");
		Files.createDirectories(crdsDir);
		Files.writeString(crdsDir.resolve("crd.yaml"),
				"apiVersion: apiextensions.k8s.io/v1\nkind: CustomResourceDefinition");

		// Load chart
		Chart chart = chartLoader.load(chartDir.toFile());

		// Verify everything loaded
		assertNotNull(chart);
		assertNotNull(chart.getMetadata());
		assertEquals("complete-chart", chart.getMetadata().getName());
		assertEquals("2.0.0", chart.getMetadata().getVersion());
		assertNotNull(chart.getReadme());
		assertTrue(chart.getReadme().contains("Complete Chart"));
		assertNotNull(chart.getValues());
		assertFalse(chart.getValues().isEmpty());
		assertNotNull(chart.getTemplates());
		assertEquals(1, chart.getTemplates().size());
		assertNotNull(chart.getCrds());
		assertEquals(1, chart.getCrds().size());
	}

	@Test
	void testLoadNonExistentChartThrowsException() {
		File nonExistentDir = new File("/nonexistent/path");
		ChartLoadException ex = assertThrows(ChartLoadException.class, () -> chartLoader.load(nonExistentDir));
		assertNotNull(ex.getChartPath());
		assertNotNull(ex.getSuggestion());
		assertTrue(ex.getMessage().contains("does not exist"));
	}

	@Test
	void testLoadChartWithoutChartYamlThrowsException() throws Exception {
		Path chartDir = tempDir.resolve("no-chart-yaml");
		Files.createDirectories(chartDir);

		ChartLoadException ex = assertThrows(ChartLoadException.class, () -> chartLoader.load(chartDir.toFile()));
		assertTrue(ex.getMessage().contains("Chart.yaml not found"));
		assertNotNull(ex.getSuggestion());
	}

	@Test
	void testLoadChartWithFiles() throws Exception {
		Path chartDir = tempDir.resolve("chart-with-files");
		Files.createDirectories(chartDir);
		Files.writeString(chartDir.resolve("Chart.yaml"), """
				apiVersion: v2
				name: chart-with-files
				version: 1.0.0
				""");
		Files.writeString(chartDir.resolve("values.yaml"), "{}");
		Files.createDirectories(chartDir.resolve("templates"));

		// Create non-template files
		Path filesDir = chartDir.resolve("config");
		Files.createDirectories(filesDir);
		Files.writeString(filesDir.resolve("app.conf"), "port=8080");
		Files.writeString(chartDir.resolve("extra.txt"), "extra content");

		Chart chart = chartLoader.load(chartDir.toFile());

		assertNotNull(chart.getFiles());
		assertFalse(chart.getFiles().isEmpty());
		assertEquals("port=8080", chart.getFiles().get("config/app.conf"));
		assertEquals("extra content", chart.getFiles().get("extra.txt"));
	}

	@Test
	void testLoadChartFilesExcludesSpecialDirsAndFiles() throws Exception {
		Path chartDir = tempDir.resolve("chart-exclusions");
		Files.createDirectories(chartDir);
		Files.writeString(chartDir.resolve("Chart.yaml"), """
				apiVersion: v2
				name: chart-exclusions
				version: 1.0.0
				""");
		Files.writeString(chartDir.resolve("values.yaml"), "{}");
		Files.createDirectories(chartDir.resolve("templates"));
		Files.createDirectories(chartDir.resolve("charts"));
		Files.createDirectories(chartDir.resolve("crds"));

		// Files that should be excluded
		Files.writeString(chartDir.resolve(".helmignore"), "*.bak");
		Files.writeString(chartDir.resolve("Chart.lock"), "lock content");

		// File that should be included
		Files.writeString(chartDir.resolve("LICENSE"), "MIT");

		Chart chart = chartLoader.load(chartDir.toFile());

		assertTrue(chart.getFiles().containsKey("LICENSE"));
		assertFalse(chart.getFiles().containsKey("Chart.yaml"));
		assertFalse(chart.getFiles().containsKey("Chart.lock"));
		assertFalse(chart.getFiles().containsKey("values.yaml"));
		assertFalse(chart.getFiles().containsKey(".helmignore"));
	}

	@Test
	void testLoadChartWithValuesSchema() throws Exception {
		Path chartDir = tempDir.resolve("chart-with-schema");
		Files.createDirectories(chartDir);
		Files.writeString(chartDir.resolve("Chart.yaml"), """
				apiVersion: v2
				name: chart-with-schema
				version: 1.0.0
				""");
		Files.writeString(chartDir.resolve("values.yaml"), "replicas: 1\n");
		Files.createDirectories(chartDir.resolve("templates"));
		Files.writeString(chartDir.resolve("values.schema.json"), """
				{
				  "$schema": "https://json-schema.org/draft-07/schema",
				  "type": "object",
				  "properties": {
				    "replicas": { "type": "integer", "minimum": 1 }
				  }
				}
				""");

		Chart chart = chartLoader.load(chartDir.toFile());

		assertNotNull(chart.getValuesSchema());
		assertTrue(chart.getValuesSchema().contains("$schema"));
	}

	@Test
	void testLoadChartWithoutValuesSchema() throws Exception {
		Path chartDir = tempDir.resolve("chart-without-schema");
		Files.createDirectories(chartDir);
		Files.writeString(chartDir.resolve("Chart.yaml"), """
				apiVersion: v2
				name: chart-without-schema
				version: 1.0.0
				""");
		Files.writeString(chartDir.resolve("values.yaml"), "replicas: 1\n");
		Files.createDirectories(chartDir.resolve("templates"));

		Chart chart = chartLoader.load(chartDir.toFile());

		assertNull(chart.getValuesSchema());
	}

	@Test
	void testLoadV1ChartWithRequirementsYaml() throws Exception {
		// v1 charts store dependencies in requirements.yaml, not Chart.yaml
		Path chartDir = tempDir.resolve("v1-chart");
		Files.createDirectories(chartDir);
		Files.writeString(chartDir.resolve("Chart.yaml"), """
				apiVersion: v1
				name: v1-chart
				version: 3.0.0
				""");
		Files.writeString(chartDir.resolve("values.yaml"), "{}");
		Files.writeString(chartDir.resolve("requirements.yaml"), """
				dependencies:
				  - name: my-operator
				    version: 2.0.0
				    repository: https://example.com
				    condition: app.operator.enabled
				    alias: operator
				  - name: kube-state-metrics
				    version: 1.0.0
				    repository: https://example.com
				    condition: monitoring.enabled
				""");
		Files.createDirectories(chartDir.resolve("templates"));

		Chart chart = chartLoader.load(chartDir.toFile());

		assertNotNull(chart.getMetadata().getDependencies());
		assertEquals(2, chart.getMetadata().getDependencies().size());

		var op = chart.getMetadata().getDependencies().get(0);
		assertEquals("my-operator", op.getName());
		assertEquals("operator", op.getAlias());
		assertEquals("app.operator.enabled", op.getCondition());

		var ksm = chart.getMetadata().getDependencies().get(1);
		assertEquals("kube-state-metrics", ksm.getName());
		assertEquals("monitoring.enabled", ksm.getCondition());
		assertNull(ksm.getAlias());
	}

	@Test
	void testLoadV2ChartIgnoresRequirementsYaml() throws Exception {
		// v2 charts with dependencies in Chart.yaml should not load requirements.yaml
		Path chartDir = tempDir.resolve("v2-chart");
		Files.createDirectories(chartDir);
		Files.writeString(chartDir.resolve("Chart.yaml"), """
				apiVersion: v2
				name: v2-chart
				version: 1.0.0
				dependencies:
				  - name: redis
				    version: "17.0.0"
				    repository: https://charts.bitnami.com/bitnami
				""");
		Files.writeString(chartDir.resolve("values.yaml"), "{}");
		// This requirements.yaml should be ignored since Chart.yaml has dependencies
		Files.writeString(chartDir.resolve("requirements.yaml"), """
				dependencies:
				  - name: stale-dep
				    version: 0.0.1
				    repository: https://old.example.com
				""");
		Files.createDirectories(chartDir.resolve("templates"));

		Chart chart = chartLoader.load(chartDir.toFile());

		assertEquals(1, chart.getMetadata().getDependencies().size());
		assertEquals("redis", chart.getMetadata().getDependencies().get(0).getName());
	}

	@Test
	void testTxtAndJsonFilesLoadedAsTemplates() throws Exception {
		Path chartDir = tempDir.resolve("chart-txt-templates");
		Files.createDirectories(chartDir);
		Files.writeString(chartDir.resolve("Chart.yaml"), """
				apiVersion: v2
				name: minio
				version: 1.0.0
				""");
		Files.writeString(chartDir.resolve("values.yaml"), "{}");
		Path tmplDir = Files.createDirectories(chartDir.resolve("templates"));
		Files.writeString(tmplDir.resolve("configmap.yaml"), "kind: ConfigMap");
		Files.writeString(tmplDir.resolve("_helpers.tpl"), "{{- define \"test\" -}}ok{{- end -}}");
		Files.writeString(tmplDir.resolve("_helper_create_bucket.txt"), "#!/bin/sh\necho create");
		Files.writeString(tmplDir.resolve("policy.json"), "{\"policy\": true}");
		Files.writeString(tmplDir.resolve("NOTES.txt"), "Thank you for installing");

		Chart chart = chartLoader.load(chartDir.toFile());

		List<String> names = chart.getTemplates().stream().map(Chart.Template::getName).toList();
		assertTrue(names.contains("configmap.yaml"));
		assertTrue(names.contains("_helpers.tpl"));
		assertTrue(names.contains("_helper_create_bucket.txt"), ".txt templates should be loaded");
		assertTrue(names.contains("policy.json"), ".json templates should be loaded");
		assertTrue(names.contains("NOTES.txt"), "NOTES.txt should be loaded");
	}

}
