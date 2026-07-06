package org.alexmond.jhelm.app.command;

import java.io.File;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.alexmond.jhelm.core.action.DependencyUpdateAction;
import org.alexmond.jhelm.core.action.PackageAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackageCommandTest {

	@Mock
	private PackageAction packageAction;

	@Mock
	private DependencyUpdateAction dependencyUpdateAction;

	private PackageCommand packageCommand;

	private final ByteArrayOutputStream outStream = new ByteArrayOutputStream();

	private final ByteArrayOutputStream errStream = new ByteArrayOutputStream();

	private final PrintStream originalOut = System.out;

	private final PrintStream originalErr = System.err;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		packageCommand = new PackageCommand(packageAction, dependencyUpdateAction);
		System.setOut(new PrintStream(outStream));
		System.setErr(new PrintStream(errStream));
	}

	@AfterEach
	void tearDown() {
		System.setOut(originalOut);
		System.setErr(originalErr);
	}

	@Test
	void testPackageChart() throws Exception {
		File archive = new File("test-chart-1.0.0.tgz");
		when(packageAction.packageChart(eq("./my-chart"), any(), any(), any(), any())).thenReturn(archive);

		CommandLine cmd = new CommandLine(packageCommand);
		cmd.execute("./my-chart");

		verify(packageAction).setDestination(any(File.class));
		verify(packageAction).packageChart(eq("./my-chart"), isNull(), isNull(), isNull(), isNull());
		assertTrue(outStream.toString().contains("Successfully packaged chart"));
	}

	@Test
	void testPackageWithDestination() throws Exception {
		File archive = new File("test-chart-1.0.0.tgz");
		when(packageAction.packageChart(eq("./my-chart"), any(), any(), any(), any())).thenReturn(archive);

		CommandLine cmd = new CommandLine(packageCommand);
		cmd.execute("./my-chart", "-d", "/tmp/output");

		verify(packageAction).setDestination(new File("/tmp/output"));
		verify(packageAction).packageChart(eq("./my-chart"), isNull(), isNull(), isNull(), isNull());
	}

	@Test
	void testPackageWithVersionOverrides() throws Exception {
		File archive = new File("test-chart-2.0.0.tgz");
		when(packageAction.packageChart(eq("./my-chart"), any(), any(), any(), any())).thenReturn(archive);

		CommandLine cmd = new CommandLine(packageCommand);
		cmd.execute("./my-chart", "--version", "2.0.0", "--app-version", "9.9");

		// version/app-version thread through to the packager (positions 4 and 5).
		verify(packageAction).packageChart(eq("./my-chart"), isNull(), isNull(), eq("2.0.0"), eq("9.9"));
	}

	@Test
	void testPackageWithDependencyUpdate() throws Exception {
		File archive = new File("test-chart-1.0.0.tgz");
		when(packageAction.packageChart(eq("./my-chart"), any(), any(), any(), any())).thenReturn(archive);

		CommandLine cmd = new CommandLine(packageCommand);
		cmd.execute("./my-chart", "-u");

		verify(dependencyUpdateAction).update(eq(new File("./my-chart")), any(), eq(false));
		verify(packageAction).packageChart(eq("./my-chart"), isNull(), isNull(), isNull(), isNull());
	}

	@Test
	void testPackageWithSigning() throws Exception {
		Path passphraseFile = tempDir.resolve("passphrase.txt");
		Files.writeString(passphraseFile, "secret123");

		File archive = new File("test-chart-1.0.0.tgz");
		when(packageAction.packageChart(eq("./my-chart"), anyString(), eq("user@example.com"), any(char[].class), any(),
				any()))
			.thenReturn(archive);

		CommandLine cmd = new CommandLine(packageCommand);
		cmd.execute("./my-chart", "--sign", "--key", "user@example.com", "--keyring", "/path/to/keyring.gpg",
				"--passphrase-file", passphraseFile.toString());

		verify(packageAction).packageChart(eq("./my-chart"), eq("/path/to/keyring.gpg"), eq("user@example.com"),
				any(char[].class), isNull(), isNull());
	}

	@Test
	void testPackageSignRequiresKey() {
		CommandLine cmd = new CommandLine(packageCommand);
		cmd.execute("./my-chart", "--sign");

		assertTrue(errStream.toString().contains("--key is required"));
	}

	@Test
	void testPackageWithError() throws Exception {
		when(packageAction.packageChart(eq("./my-chart"), any(), any(), any(), any()))
			.thenThrow(new RuntimeException("chart not found"));

		CommandLine cmd = new CommandLine(packageCommand);
		cmd.execute("./my-chart");

		assertTrue(errStream.toString().contains("Error packaging chart"));
	}

	@Test
	void testPackageSignWithEnvPassphrase() throws Exception {
		// When no passphrase file and no env var, should error
		File archive = new File("test-chart-1.0.0.tgz");
		when(packageAction.packageChart(eq("./my-chart"), anyString(), eq("user@example.com"), any(char[].class), any(),
				any()))
			.thenReturn(archive);

		CommandLine cmd = new CommandLine(packageCommand);
		cmd.execute("./my-chart", "--sign", "--key", "user@example.com");

		// Will fail because no passphrase source — error about passphrase expected
		String output = errStream.toString();
		assertTrue(output.contains("Error") || output.contains("Passphrase"),
				"Expected passphrase error, got: " + output);
	}

}
