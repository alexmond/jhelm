package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.model.RepositoryConfig;
import org.alexmond.jhelm.core.service.RepoManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
		doNothing().when(repoManager).pull(anyString(), isNull(), anyString());

		CommandLine cmd = new CommandLine(pullCommand);
		cmd.execute("oci://ghcr.io/helm/charts/nginx:1.0.0");

		verify(repoManager).pull("oci://ghcr.io/helm/charts/nginx:1.0.0", null, ".");
	}

	@Test
	void testPullRepoChartWithVersion() throws IOException {
		doNothing().when(repoManager).pull(anyString(), anyString(), anyString());

		CommandLine cmd = new CommandLine(pullCommand);
		cmd.execute("bitnami/nginx", "--version", "19.0.0");

		verify(repoManager).pull("bitnami/nginx", "19.0.0", ".");
	}

	@Test
	void testPullRepoChartMissingVersion() throws IOException {
		doThrow(new IOException("version is required for repository chart pulls")).when(repoManager)
			.pull(eq("bitnami/nginx"), isNull(), eq("."));

		CommandLine cmd = new CommandLine(pullCommand);
		cmd.execute("bitnami/nginx");
		// Should log error, no crash
	}

	@Test
	void testPullWithError() throws IOException {
		doThrow(new IOException("connection refused")).when(repoManager).pull(anyString(), isNull(), anyString());

		CommandLine cmd = new CommandLine(pullCommand);
		cmd.execute("oci://ghcr.io/helm/charts/nginx:1.0.0");
		// Should log error without throwing
	}

	@Test
	void testPullWithRepoUrlAndAuth() throws IOException {
		doNothing().when(repoManager)
			.pullFromRepoUrl(anyString(), anyString(), anyString(), anyString(),
					any(RepositoryConfig.Repository.class));

		CommandLine cmd = new CommandLine(pullCommand);
		cmd.execute("mychart", "--repo", "https://charts.example.com", "--version", "1.0.0", "--username", "u",
				"--password", "p", "--insecure-skip-tls-verify");

		ArgumentCaptor<RepositoryConfig.Repository> auth = ArgumentCaptor.forClass(RepositoryConfig.Repository.class);
		verify(repoManager).pullFromRepoUrl(eq("https://charts.example.com"), eq("mychart"), eq("1.0.0"), eq("."),
				auth.capture());
		RepositoryConfig.Repository built = auth.getValue();
		assertEquals("u", built.getUsername());
		assertEquals("p", built.getPassword());
		assertTrue(built.isInsecureSkipTlsVerify());
	}

	@Test
	void testPullWithDestOption() throws IOException {
		doNothing().when(repoManager).pull(anyString(), isNull(), anyString());

		CommandLine cmd = new CommandLine(pullCommand);
		cmd.execute("oci://ghcr.io/helm/charts/nginx:1.0.0", "--dest", "/tmp/charts");

		verify(repoManager).pull("oci://ghcr.io/helm/charts/nginx:1.0.0", null, "/tmp/charts");
	}

}
