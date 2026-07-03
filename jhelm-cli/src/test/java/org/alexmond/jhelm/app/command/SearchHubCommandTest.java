package org.alexmond.jhelm.app.command;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.alexmond.jhelm.core.action.SearchHubAction;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SearchHubCommandTest {

	@Mock
	private SearchHubAction searchHubAction;

	private SearchHubCommand searchHubCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		searchHubCommand = new SearchHubCommand(searchHubAction);
	}

	@Test
	void testSearchHubSuccess() throws Exception {
		SearchHubAction.HubResult result = new SearchHubAction.HubResult();
		result.setName("nginx");
		result.setDescription("A chart for nginx");
		result.setVersion("15.0.0");
		result.setAppVersion("1.25.0");
		result.setRepoName("bitnami");
		result.setRepoUrl("https://charts.bitnami.com/bitnami");

		when(searchHubAction.search(anyString(), anyInt())).thenReturn(List.of(result));

		CommandLine cmd = new CommandLine(searchHubCommand);
		int exitCode = cmd.execute("nginx");
		assertEquals(0, exitCode);
	}

	@Test
	void testSearchHubDefaultShowsArtifactHubUrl() {
		when(searchHubAction.search(anyString(), anyInt())).thenReturn(List.of(sampleResult()));

		String out = captureStdout(() -> new CommandLine(searchHubCommand).execute("nginx"));

		assertTrue(out.contains("https://artifacthub.io/packages/helm/bitnami/nginx"), out);
		assertFalse(out.contains("charts.bitnami.com"), "default output should not show the repo URL: " + out);
	}

	@Test
	void testSearchHubListRepoUrlShowsRepositoryUrl() {
		when(searchHubAction.search(anyString(), anyInt())).thenReturn(List.of(sampleResult()));

		String out = captureStdout(() -> new CommandLine(searchHubCommand).execute("nginx", "--list-repo-url"));

		assertTrue(out.contains("https://charts.bitnami.com/bitnami"), out);
		assertFalse(out.contains("artifacthub.io"),
				"--list-repo-url output should not show the Artifact Hub URL: " + out);
	}

	private static SearchHubAction.HubResult sampleResult() {
		SearchHubAction.HubResult result = new SearchHubAction.HubResult();
		result.setName("nginx");
		result.setDescription("A chart for nginx");
		result.setVersion("15.0.0");
		result.setAppVersion("1.25.0");
		result.setRepoName("bitnami");
		result.setRepoUrl("https://charts.bitnami.com/bitnami");
		return result;
	}

	private static String captureStdout(Runnable action) {
		PrintStream original = System.out;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
		try {
			action.run();
		}
		finally {
			System.setOut(original);
		}
		return buffer.toString(StandardCharsets.UTF_8);
	}

	@Test
	void testSearchHubNoResults() throws Exception {
		when(searchHubAction.search(anyString(), anyInt())).thenReturn(List.of());

		CommandLine cmd = new CommandLine(searchHubCommand);
		int exitCode = cmd.execute("nonexistent");
		assertEquals(0, exitCode);
	}

	@Test
	void testSearchHubError() throws Exception {
		when(searchHubAction.search(anyString(), anyInt())).thenThrow(new JhelmException("connection failed"));

		CommandLine cmd = new CommandLine(searchHubCommand);
		int exitCode = cmd.execute("nginx");
		// #647: a failed hub search must exit non-zero so callers can detect it.
		assertEquals(CommandLine.ExitCode.SOFTWARE, exitCode);
	}

}
