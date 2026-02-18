package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.ChartLoader;
import org.alexmond.jhelm.core.ShowAction;
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
    private ShowAction showAction;

    @BeforeEach
    void setUp() throws Exception {
        // Redirect System.out to capture command output
        System.setOut(new PrintStream(outputStream));

        ChartLoader chartLoader = new ChartLoader();
        showAction = new ShowAction(chartLoader);

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
        ShowCommand.ChartCommand command = new ShowCommand.ChartCommand(showAction);
        command.chartPath = chartDir.toString();

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("name: test-chart"), "Output: " + output);
        assertTrue(output.contains("version:"), "Output: " + output);
        assertTrue(output.contains("1.0.0"), "Output: " + output);
        assertTrue(output.contains("description:"), "Output: " + output);
        assertTrue(output.contains("A test chart"), "Output: " + output);
        assertTrue(output.contains("type: application"), "Output: " + output);
    }

    @Test
    void testShowValues() {
        ShowCommand.ValuesCommand command = new ShowCommand.ValuesCommand(showAction);
        command.chartPath = chartDir.toString();

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("replicaCount: 1"), "Output: " + output);
        assertTrue(output.contains("repository: nginx"), "Output: " + output);
        assertTrue(output.contains("1.21"), "Output: " + output);
        assertTrue(output.contains("port: 80"), "Output: " + output);
    }

    @Test
    void testShowReadme() {
        ShowCommand.ReadmeCommand command = new ShowCommand.ReadmeCommand(showAction);
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
        ShowCommand.CrdsCommand command = new ShowCommand.CrdsCommand(showAction);
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
        ShowCommand.AllCommand command = new ShowCommand.AllCommand(showAction);
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

        ShowCommand.ReadmeCommand command = new ShowCommand.ReadmeCommand(showAction);
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

        ShowCommand.CrdsCommand command = new ShowCommand.CrdsCommand(showAction);
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

        ShowCommand.ValuesCommand command = new ShowCommand.ValuesCommand(showAction);
        command.chartPath = emptyValuesDir.toString();

        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("{}"));
    }

    @Test
    void testShowCommandWithInvalidPath() {
        ShowCommand.ChartCommand command = new ShowCommand.ChartCommand(showAction);
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

    @Test
    void testShowCommandRunShowsUsage() {
        outputStream.reset();
        ShowCommand showCommand = new ShowCommand();
        showCommand.run();

        String output = outputStream.toString();
        assertTrue(output.contains("show") || output.contains("Usage"));
    }

    @Test
    void testShowChartCommandWithError() throws Exception {
        Path invalidDir = tempDir.resolve("nonexistent");
        ShowCommand.ChartCommand command = new ShowCommand.ChartCommand(showAction);
        command.chartPath = invalidDir.toString();

        // This should log an error but not throw
        command.run();

        // Verify it attempted to call showAction
        // (the actual error handling is done by ShowAction throwing an exception)
    }

    @Test
    void testShowValuesCommandWithError() throws Exception {
        Path invalidDir = tempDir.resolve("nonexistent");
        ShowCommand.ValuesCommand command = new ShowCommand.ValuesCommand(showAction);
        command.chartPath = invalidDir.toString();

        command.run();
        // Should handle exception gracefully
    }

    @Test
    void testShowAllCommandWithError() throws Exception {
        Path invalidDir = tempDir.resolve("nonexistent");
        ShowCommand.AllCommand command = new ShowCommand.AllCommand(showAction);
        command.chartPath = invalidDir.toString();

        command.run();
        // Should handle exception gracefully
    }

    @Test
    void testShowChartCommandDirectExecution() {
        ShowCommand.ChartCommand command = new ShowCommand.ChartCommand(showAction);
        command.chartPath = chartDir.toString();

        // Direct run() execution
        command.run();

        String output = outputStream.toString();
        assertTrue(output.contains("test-chart"));
    }

    @Test
    void testShowReadmeCommandWithError() throws Exception {
        Path invalidDir = tempDir.resolve("nonexistent");
        ShowCommand.ReadmeCommand command = new ShowCommand.ReadmeCommand(showAction);
        command.chartPath = invalidDir.toString();

        command.run();
        // Should handle exception gracefully
    }

    @Test
    void testShowCrdsCommandWithError() throws Exception {
        Path invalidDir = tempDir.resolve("nonexistent");
        ShowCommand.CrdsCommand command = new ShowCommand.CrdsCommand(showAction);
        command.chartPath = invalidDir.toString();

        command.run();
        // Should handle exception gracefully
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // Restore System.out
        System.setOut(originalOut);
    }
}
