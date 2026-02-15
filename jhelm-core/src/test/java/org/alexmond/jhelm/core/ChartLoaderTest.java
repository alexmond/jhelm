package org.alexmond.jhelm.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChartLoaderTest {

    private ChartLoader chartLoader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        chartLoader = new ChartLoader();
    }

    @Test
    void testLoadChartWithReadme() throws IOException {
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
    void testLoadChartWithCrds() throws IOException {
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
    void testLoadChartWithoutReadme() throws IOException {
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
    void testLoadChartWithoutCrds() throws IOException {
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
    void testLoadChartWithEverything() throws IOException {
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
        Files.writeString(crdsDir.resolve("crd.yaml"), "apiVersion: apiextensions.k8s.io/v1\nkind: CustomResourceDefinition");

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
        assertThrows(IllegalArgumentException.class, () -> chartLoader.load(nonExistentDir));
    }

    @Test
    void testLoadChartWithoutChartYamlThrowsException() throws IOException {
        Path chartDir = tempDir.resolve("no-chart-yaml");
        Files.createDirectories(chartDir);

        assertThrows(IllegalArgumentException.class, () -> chartLoader.load(chartDir.toFile()));
    }
}
