package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.ChartLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ShowCommandTest {

    @TempDir
    Path tempDir;

    private Path chartDir;
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() throws Exception {
        // Redirect System.out to capture command output
        System.setOut(new PrintStream(outputStream));

        // Create a test chart
        chartDir = tempDir.resolve("test-chart");
        Files.createDirectories(chartDir);

        // Create Chart.yaml
        String chartYaml = """
                apiVersion: v2
                name: test-chart
                version: 1.0.0
                description: A test chart
                appVersion: "1.0"
                type: application
                """;
        Files.writeString(chartDir.resolve("Chart.yaml"), chartYaml);

        // Create README.md
        String readme = """
                # Test Chart

                This is a comprehensive test chart.

                ## Installation

                Use helm install to install this chart.
                """;
        Files.writeString(chartDir.resolve("README.md"), readme);

        // Create values.yaml
        String values = """
                replicaCount: 1
                image:
                  repository: nginx
                  tag: "1.21"
                  pullPolicy: IfNotPresent
                service:
                  type: ClusterIP
                  port: 80
                """;
        Files.writeString(chartDir.resolve("values.yaml"), values);

        // Create templates directory
        Path templatesDir = chartDir.resolve("templates");
        Files.createDirectories(templatesDir);
        Files.writeString(templatesDir.resolve("deployment.yaml"), "apiVersion: apps/v1\nkind: Deployment");

        // Create crds directory
        Path crdsDir = chartDir.resolve("crds");
        Files.createDirectories(crdsDir);

        String crd1 = """
                apiVersion: apiextensions.k8s.io/v1
                kind: CustomResourceDefinition
                metadata:
                  name: mycrd.example.com
                spec:
                  group: example.com
                  names:
                    kind: MyCRD
                    plural: mycrds
                  scope: Namespaced
                  versions:
                  - name: v1
                    served: true
                    storage: true
                """;
        Files.writeString(crdsDir.resolve("mycrd.yaml"), crd1);

        String crd2 = """
                apiVersion: apiextensions.k8s.io/v1
                kind: CustomResourceDefinition
                metadata:
                  name: anothercrd.example.com
                spec:
                  group: example.com
                  names:
                    kind: AnotherCRD
                    plural: anothercrds
                  scope: Cluster
                  versions:
                  - name: v1alpha1
                    served: true
                    storage: true
                """;
        Files.writeString(crdsDir.resolve("anothercrd.yaml"), crd2);
    }

    @Test
    void testShowChart() {
        ShowCommand.ChartCommand command = new ShowCommand.ChartCommand(new ChartLoader());
        command.chartPath = chartDir.toString();

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("name: test-chart"));
        assertTrue(output.contains("version: \"1.0.0\""));
        assertTrue(output.contains("description: A test chart"));
        assertTrue(output.contains("appVersion: \"1.0\""));
        assertTrue(output.contains("type: application"));
    }

    @Test
    void testShowValues() {
        ShowCommand.ValuesCommand command = new ShowCommand.ValuesCommand(new ChartLoader());
        command.chartPath = chartDir.toString();

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("replicaCount: 1"));
        assertTrue(output.contains("repository: nginx"));
        assertTrue(output.contains("tag: \"1.21\""));
        assertTrue(output.contains("type: ClusterIP"));
        assertTrue(output.contains("port: 80"));
    }

    @Test
    void testShowReadme() {
        ShowCommand.ReadmeCommand command = new ShowCommand.ReadmeCommand(new ChartLoader());
        command.chartPath = chartDir.toString();

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("# Test Chart"));
        assertTrue(output.contains("comprehensive test chart"));
        assertTrue(output.contains("## Installation"));
        assertTrue(output.contains("helm install"));
    }

    @Test
    void testShowCrds() {
        ShowCommand.CrdsCommand command = new ShowCommand.CrdsCommand(new ChartLoader());
        command.chartPath = chartDir.toString();

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("kind: CustomResourceDefinition"));
        assertTrue(output.contains("mycrd.example.com"));
        assertTrue(output.contains("anothercrd.example.com"));
        assertTrue(output.contains("MyCRD"));
        assertTrue(output.contains("AnotherCRD"));
        assertTrue(output.contains("---")); // Separator between CRDs
    }

    @Test
    void testShowAll() {
        ShowCommand.AllCommand command = new ShowCommand.AllCommand(new ChartLoader());
        command.chartPath = chartDir.toString();

        command.run();

        String output = outputStream.toString();

        // Should contain chart metadata
        assertTrue(output.contains("# Chart.yaml"));
        assertTrue(output.contains("name: test-chart"));

        // Should contain values
        assertTrue(output.contains("# values.yaml"));
        assertTrue(output.contains("replicaCount: 1"));

        // Should contain README
        assertTrue(output.contains("# README.md"));
        assertTrue(output.contains("Test Chart"));

        // Should contain CRDs
        assertTrue(output.contains("# CRDs"));
        assertTrue(output.contains("CustomResourceDefinition"));

        // Should contain separators
        assertTrue(output.contains("---"));
    }

    @Test
    void testShowReadmeWhenNotPresent() throws Exception {
        // Create a chart without README
        Path noReadmeDir = tempDir.resolve("no-readme-chart");
        Files.createDirectories(noReadmeDir);

        String chartYaml = """
                apiVersion: v2
                name: no-readme-chart
                version: 1.0.0
                """;
        Files.writeString(noReadmeDir.resolve("Chart.yaml"), chartYaml);
        Files.writeString(noReadmeDir.resolve("values.yaml"), "{}");
        Files.createDirectories(noReadmeDir.resolve("templates"));

        ShowCommand.ReadmeCommand command = new ShowCommand.ReadmeCommand(new ChartLoader());
        command.chartPath = noReadmeDir.toString();

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("No README found in chart"));
    }

    @Test
    void testShowCrdsWhenNotPresent() throws Exception {
        // Create a chart without CRDs
        Path noCrdsDir = tempDir.resolve("no-crds-chart");
        Files.createDirectories(noCrdsDir);

        String chartYaml = """
                apiVersion: v2
                name: no-crds-chart
                version: 1.0.0
                """;
        Files.writeString(noCrdsDir.resolve("Chart.yaml"), chartYaml);
        Files.writeString(noCrdsDir.resolve("values.yaml"), "{}");
        Files.createDirectories(noCrdsDir.resolve("templates"));

        ShowCommand.CrdsCommand command = new ShowCommand.CrdsCommand(new ChartLoader());
        command.chartPath = noCrdsDir.toString();

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("No CRDs found in chart"));
    }

    @Test
    void testShowValuesWhenEmpty() throws Exception {
        // Create a chart with empty values
        Path emptyValuesDir = tempDir.resolve("empty-values-chart");
        Files.createDirectories(emptyValuesDir);

        String chartYaml = """
                apiVersion: v2
                name: empty-values-chart
                version: 1.0.0
                """;
        Files.writeString(emptyValuesDir.resolve("Chart.yaml"), chartYaml);
        Files.writeString(emptyValuesDir.resolve("values.yaml"), "{}");
        Files.createDirectories(emptyValuesDir.resolve("templates"));

        ShowCommand.ValuesCommand command = new ShowCommand.ValuesCommand(new ChartLoader());
        command.chartPath = emptyValuesDir.toString();

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("{}"));
    }

    @Test
    void testShowCommandWithInvalidPath() {
        ShowCommand.ChartCommand command = new ShowCommand.ChartCommand(new ChartLoader());
        command.chartPath = "/nonexistent/path";

        // Should not throw, but should log error
        command.run();

        // Verify error was logged (output will be empty since we redirected System.out)
        // In a real scenario, this would be verified with a logging framework test
    }

    @Test
    void testShowCommandHelp() {
        ShowCommand showCommand = new ShowCommand();
        CommandLine cmd = new CommandLine(showCommand);

        String help = cmd.getUsageMessage();

        assertTrue(help.contains("show"));
        assertTrue(help.contains("Show information about a chart"));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // Restore System.out
        System.setOut(originalOut);
    }
}
