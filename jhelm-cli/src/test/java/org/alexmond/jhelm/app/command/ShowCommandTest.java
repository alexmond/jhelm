package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.service.SignatureService;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.VerifyAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;

class ShowCommandTest {

	@TempDir
	Path tempDir;

	private Path chartDir;

	private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

	private final PrintStream originalOut = System.out;

	private ShowAction showAction;

	private ChartResolver chartResolver;

	@BeforeEach
	void setUp() throws Exception {
		// Redirect System.out to capture command output
		System.setOut(new PrintStream(outputStream));

		ChartLoader chartLoader = new ChartLoader();
		showAction = new ShowAction(chartLoader);
		// Local chart paths resolve directly (no repo pull), so the
		// RepoManager/VerifyAction
		// wiring here is only to satisfy the resolver's constructor.
		chartResolver = new ChartResolver(chartLoader, new RepoManager(), new VerifyAction(new SignatureService()));

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
		ShowCommand.ChartCommand command = new ShowCommand.ChartCommand(showAction, chartResolver);
		command.chartPath = chartDir.toString();

		command.call();

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
		ShowCommand.ValuesCommand command = new ShowCommand.ValuesCommand(showAction, chartResolver);
		command.chartPath = chartDir.toString();

		command.call();

		String output = outputStream.toString();
		assertTrue(output.contains("replicaCount: 1"), "Output: " + output);
		assertTrue(output.contains("repository: nginx"), "Output: " + output);
		assertTrue(output.contains("1.21"), "Output: " + output);
		assertTrue(output.contains("port: 80"), "Output: " + output);
	}

	@Test
	void testShowReadme() {
		ShowCommand.ReadmeCommand command = new ShowCommand.ReadmeCommand(showAction, chartResolver);
		command.chartPath = chartDir.toString();

		command.call();

		String output = outputStream.toString();
		assertTrue(output.contains("# Test Chart"));
		assertTrue(output.contains("comprehensive test chart"));
		assertTrue(output.contains("## Installation"));
		assertTrue(output.contains("helm install"));
	}

	@Test
	void testShowCrds() {
		ShowCommand.CrdsCommand command = new ShowCommand.CrdsCommand(showAction, chartResolver);
		command.chartPath = chartDir.toString();

		command.call();

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
		ShowCommand.AllCommand command = new ShowCommand.AllCommand(showAction, chartResolver);
		command.chartPath = chartDir.toString();

		command.call();

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

		ShowCommand.ReadmeCommand command = new ShowCommand.ReadmeCommand(showAction, chartResolver);
		command.chartPath = noReadmeDir.toString();

		command.call();

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

		ShowCommand.CrdsCommand command = new ShowCommand.CrdsCommand(showAction, chartResolver);
		command.chartPath = noCrdsDir.toString();

		command.call();

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

		ShowCommand.ValuesCommand command = new ShowCommand.ValuesCommand(showAction, chartResolver);
		command.chartPath = emptyValuesDir.toString();

		command.call();

		String output = outputStream.toString();
		assertTrue(output.contains("{}"));
	}

	@Test
	void testShowCommandWithInvalidPath() {
		ShowCommand.ChartCommand command = new ShowCommand.ChartCommand(showAction, chartResolver);
		command.chartPath = "/nonexistent/path";

		// Should not throw, but should log error
		command.call();

		// Verify error was logged (output will be empty since we redirected System.out)
		// In a real scenario, this would be verified with a logging framework test
	}

	// Picocli must instantiate the subcommands to resolve their @Mixin options; the
	// subcommands have no no-arg constructor, so supply a factory that builds them from
	// the
	// test collaborators (Spring does this in production).
	private CommandLine.IFactory subcommandFactory() {
		return new CommandLine.IFactory() {
			@Override
			public <K> K create(Class<K> cls) throws Exception {
				if (cls == ShowCommand.ChartCommand.class) {
					return cls.cast(new ShowCommand.ChartCommand(showAction, chartResolver));
				}
				if (cls == ShowCommand.ValuesCommand.class) {
					return cls.cast(new ShowCommand.ValuesCommand(showAction, chartResolver));
				}
				if (cls == ShowCommand.ReadmeCommand.class) {
					return cls.cast(new ShowCommand.ReadmeCommand(showAction, chartResolver));
				}
				if (cls == ShowCommand.CrdsCommand.class) {
					return cls.cast(new ShowCommand.CrdsCommand(showAction, chartResolver));
				}
				if (cls == ShowCommand.AllCommand.class) {
					return cls.cast(new ShowCommand.AllCommand(showAction, chartResolver));
				}
				return CommandLine.defaultFactory().create(cls);
			}
		};
	}

	@Test
	void testShowCommandHelp() {
		CommandLine cmd = new CommandLine(new ShowCommand(), subcommandFactory());

		String help = cmd.getUsageMessage();

		assertTrue(help.contains("show"));
		assertTrue(help.contains("Show information about a chart"));
	}

	@Test
	void testShowCommandRunShowsUsage() {
		outputStream.reset();
		// Executing with no subcommand invokes ShowCommand.call(), which prints usage via
		// the injected spec (the factory-built command line).
		new CommandLine(new ShowCommand(), subcommandFactory()).execute();

		String output = outputStream.toString();
		assertTrue(output.contains("show") || output.contains("Usage"));
	}

	@ParameterizedTest
	@MethodSource("errorCommandNames")
	void testShowSubcommandWithError(String commandName) {
		Path invalidDir = tempDir.resolve("nonexistent");
		// All subcommands should handle nonexistent paths gracefully
		switch (commandName) {
			case "chart" -> {
				var cmd = new ShowCommand.ChartCommand(showAction, chartResolver);
				cmd.chartPath = invalidDir.toString();
				cmd.call();
			}
			case "values" -> {
				var cmd = new ShowCommand.ValuesCommand(showAction, chartResolver);
				cmd.chartPath = invalidDir.toString();
				cmd.call();
			}
			case "all" -> {
				var cmd = new ShowCommand.AllCommand(showAction, chartResolver);
				cmd.chartPath = invalidDir.toString();
				cmd.call();
			}
			case "readme" -> {
				var cmd = new ShowCommand.ReadmeCommand(showAction, chartResolver);
				cmd.chartPath = invalidDir.toString();
				cmd.call();
			}
			case "crds" -> {
				var cmd = new ShowCommand.CrdsCommand(showAction, chartResolver);
				cmd.chartPath = invalidDir.toString();
				cmd.call();
			}
		}
	}

	static Stream<Arguments> errorCommandNames() {
		return Stream.of(Arguments.of("chart"), Arguments.of("values"), Arguments.of("all"), Arguments.of("readme"),
				Arguments.of("crds"));
	}

	@Test
	void testShowChartCommandDirectExecution() {
		ShowCommand.ChartCommand command = new ShowCommand.ChartCommand(showAction, chartResolver);
		command.chartPath = chartDir.toString();

		command.call();

		String output = outputStream.toString();
		assertTrue(output.contains("test-chart"));
	}

	@AfterEach
	void tearDown() {
		// Restore System.out
		System.setOut(originalOut);
	}

}
