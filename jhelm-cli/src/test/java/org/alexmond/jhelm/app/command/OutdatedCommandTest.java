package org.alexmond.jhelm.app.command;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Optional;

import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.service.RepoManager.ChartMatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class OutdatedCommandTest {

	@Mock
	private RepoManager repoManager;

	private OutdatedCommand command;

	private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

	private final PrintStream originalOut = System.out;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		command = new OutdatedCommand(repoManager);
		System.setOut(new PrintStream(outputStream));
	}

	@AfterEach
	void tearDown() {
		System.setOut(originalOut);
	}

	private static ChartMatch match(String repo, String version) {
		return new ChartMatch(repo, new RepoManager.ChartVersion("nginx", version, "1.21.0", "Nginx web server"));
	}

	@Test
	void testReportsLatestVersion() throws IOException {
		when(repoManager.latestOf(anyString())).thenReturn(Optional.of(match("bitnami", "1.2.0")));

		int exit = new CommandLine(command).execute("nginx");

		assertEquals(CommandLine.ExitCode.OK, exit);
		String out = outputStream.toString();
		assertTrue(out.contains("LATEST VERSION"), out);
		assertTrue(out.contains("1.2.0"), out);
		assertTrue(out.contains("bitnami"), out);
	}

	@Test
	void testUpgradeAvailable() throws IOException {
		when(repoManager.latestOf(anyString())).thenReturn(Optional.of(match("bitnami", "1.2.0")));

		int exit = new CommandLine(command).execute("nginx", "--current", "1.0.0");

		assertEquals(CommandLine.ExitCode.OK, exit);
		String out = outputStream.toString();
		assertTrue(out.contains("Upgrade available: 1.0.0 -> 1.2.0 (bitnami)"), out);
	}

	@Test
	void testUpToDate() throws IOException {
		when(repoManager.latestOf(anyString())).thenReturn(Optional.of(match("bitnami", "1.2.0")));

		int exit = new CommandLine(command).execute("nginx", "--current", "1.2.0");

		assertEquals(CommandLine.ExitCode.OK, exit);
		String out = outputStream.toString();
		assertTrue(out.contains("up to date"), out);
		assertTrue(!out.contains("Upgrade available"), out);
	}

	@Test
	void testNoVersionsFound() throws IOException {
		when(repoManager.latestOf(anyString())).thenReturn(Optional.empty());

		int exit = new CommandLine(command).execute("nonexistent");

		assertEquals(CommandLine.ExitCode.OK, exit);
		assertTrue(outputStream.toString().contains("No versions of \"nonexistent\""), outputStream.toString());
	}

	@Test
	void testJsonOutputWithUpgradeVerdict() throws IOException {
		when(repoManager.latestOf(anyString())).thenReturn(Optional.of(match("bitnami", "1.2.0")));

		int exit = new CommandLine(command).execute("nginx", "--current", "1.0.0", "-o", "json");

		assertEquals(CommandLine.ExitCode.OK, exit);
		String out = outputStream.toString();
		assertTrue(out.contains("\"latest_version\":\"1.2.0\""), out);
		assertTrue(out.contains("\"upgrade_available\":true"), out);
	}

	@Test
	void testError() throws IOException {
		when(repoManager.latestOf(anyString())).thenThrow(new IOException("boom"));

		int exit = new CommandLine(command).execute("nginx");

		assertEquals(CommandLine.ExitCode.SOFTWARE, exit);
	}

}
