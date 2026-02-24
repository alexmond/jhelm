package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.RepoManager;
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

class PushCommandTest {

	@Mock
	private RepoManager repoManager;

	private PushCommand pushCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		pushCommand = new PushCommand(repoManager);
	}

	@Test
	void testPushSuccess() throws IOException {
		doNothing().when(repoManager).pushOci(anyString(), anyString());

		CommandLine cmd = new CommandLine(pushCommand);
		cmd.execute("nginx-1.0.0.tgz", "oci://ghcr.io/myorg/charts/nginx:1.0.0");

		verify(repoManager).pushOci("nginx-1.0.0.tgz", "oci://ghcr.io/myorg/charts/nginx:1.0.0");
	}

	@Test
	void testPushWithError() throws IOException {
		doThrow(new IOException("unauthorized")).when(repoManager).pushOci(anyString(), anyString());

		CommandLine cmd = new CommandLine(pushCommand);
		cmd.execute("nginx-1.0.0.tgz", "oci://ghcr.io/myorg/charts/nginx:1.0.0");
		// Should log error without throwing
	}

}
