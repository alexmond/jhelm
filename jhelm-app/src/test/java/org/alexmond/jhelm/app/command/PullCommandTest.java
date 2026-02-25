package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.service.RepoManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.IOException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class PullCommandTest {

	@Mock
	private RepoManager repoManager;

	private PullCommand pullCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		pullCommand = new PullCommand(repoManager);
	}

	@Test
	void testPullOciChart() throws IOException {
		doNothing().when(repoManager).pullFromUrl(anyString(), anyString(), anyString());

		CommandLine cmd = new CommandLine(pullCommand);
		cmd.execute("oci://ghcr.io/helm/charts/nginx:1.0.0");

		verify(repoManager).pullFromUrl("oci://ghcr.io/helm/charts/nginx:1.0.0", ".", "nginx-1.0.0.tgz");
	}

	@Test
	void testPullRepoChartWithVersion() throws IOException {
		doNothing().when(repoManager).pull(anyString(), anyString(), anyString(), anyString());

		CommandLine cmd = new CommandLine(pullCommand);
		cmd.execute("bitnami/nginx", "--version", "19.0.0");

		verify(repoManager).pull("bitnami/nginx", null, "19.0.0", ".");
	}

	@Test
	void testPullRepoChartMissingVersion() throws IOException {
		CommandLine cmd = new CommandLine(pullCommand);
		cmd.execute("bitnami/nginx");
		// Should log error, no pull call made
	}

	@Test
	void testPullWithError() throws IOException {
		doThrow(new IOException("connection refused")).when(repoManager)
			.pullFromUrl(anyString(), anyString(), anyString());

		CommandLine cmd = new CommandLine(pullCommand);
		cmd.execute("oci://ghcr.io/helm/charts/nginx:1.0.0");
		// Should log error without throwing
	}

	@Test
	void testPullWithDestOption() throws IOException {
		doNothing().when(repoManager).pullFromUrl(anyString(), anyString(), anyString());

		CommandLine cmd = new CommandLine(pullCommand);
		cmd.execute("oci://ghcr.io/helm/charts/nginx:1.0.0", "--dest", "/tmp/charts");

		verify(repoManager).pullFromUrl("oci://ghcr.io/helm/charts/nginx:1.0.0", "/tmp/charts", "nginx-1.0.0.tgz");
	}

}
