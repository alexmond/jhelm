package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.action.VerifyAction;
import org.alexmond.jhelm.core.model.RepositoryConfig;
import org.alexmond.jhelm.core.service.RepoManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PullCommandTest {

	@Mock
	private RepoManager repoManager;

	@Mock
	private VerifyAction verifyAction;

	private PullCommand pullCommand;

	@TempDir
	Path dest;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		pullCommand = new PullCommand(repoManager, verifyAction);
	}

	// Makes the mocked pull(chart, version, destDir, withProv) write a .tgz (and,
	// when withProv, a .prov) into the work directory (arg index 2) so the command's
	// findArchive/deliver steps have something to operate on.
	private void stubPullWritesArchive(String tgzName, boolean withProv) throws IOException {
		doAnswer((inv) -> {
			String workDir = inv.getArgument(2);
			Files.writeString(Path.of(workDir, tgzName), "tgz");
			if (withProv) {
				Files.writeString(Path.of(workDir, tgzName + ".prov"), "prov");
			}
			return null;
		}).when(repoManager).pull(anyString(), any(), anyString(), anyBoolean());
	}

	@Test
	void testPullOciChart() throws IOException {
		stubPullWritesArchive("nginx-1.0.0.tgz", false);

		int exit = new CommandLine(pullCommand).execute("oci://ghcr.io/helm/charts/nginx:1.0.0", "--dest",
				dest.toString());

		assertEquals(CommandLine.ExitCode.OK, exit);
		assertTrue(dest.resolve("nginx-1.0.0.tgz").toFile().exists());
		verify(repoManager).pull(eq("oci://ghcr.io/helm/charts/nginx:1.0.0"), isNull(), anyString(), eq(false));
	}

	@Test
	void testPullRepoChartWithVersion() throws IOException {
		stubPullWritesArchive("nginx-19.0.0.tgz", false);

		int exit = new CommandLine(pullCommand).execute("bitnami/nginx", "--version", "19.0.0", "--dest",
				dest.toString());

		assertEquals(CommandLine.ExitCode.OK, exit);
		assertTrue(dest.resolve("nginx-19.0.0.tgz").toFile().exists());
		verify(repoManager).pull(eq("bitnami/nginx"), eq("19.0.0"), anyString(), eq(false));
	}

	@Test
	void testPullRepoChartMissingVersion() throws IOException {
		doThrow(new IOException("version is required for repository chart pulls")).when(repoManager)
			.pull(eq("bitnami/nginx"), isNull(), anyString(), anyBoolean());

		int exit = new CommandLine(pullCommand).execute("bitnami/nginx", "--dest", dest.toString());

		assertNotEquals(CommandLine.ExitCode.OK, exit);
	}

	@Test
	void testPullWithError() throws IOException {
		doThrow(new IOException("connection refused")).when(repoManager)
			.pull(anyString(), any(), anyString(), anyBoolean());

		int exit = new CommandLine(pullCommand).execute("oci://ghcr.io/helm/charts/nginx:1.0.0", "--dest",
				dest.toString());

		assertNotEquals(CommandLine.ExitCode.OK, exit);
	}

	@Test
	void testPullWithRepoUrlAndAuth() throws IOException {
		doAnswer((inv) -> {
			Files.writeString(Path.of(inv.getArgument(3), "mychart-1.0.0.tgz"), "tgz");
			return null;
		}).when(repoManager)
			.pullFromRepoUrl(anyString(), anyString(), anyString(), anyString(), any(RepositoryConfig.Repository.class),
					anyBoolean());

		int exit = new CommandLine(pullCommand).execute("mychart", "--repo", "https://charts.example.com", "--version",
				"1.0.0", "--username", "u", "--password", "p", "--insecure-skip-tls-verify", "--dest", dest.toString());

		assertEquals(CommandLine.ExitCode.OK, exit);
		ArgumentCaptor<RepositoryConfig.Repository> auth = ArgumentCaptor.forClass(RepositoryConfig.Repository.class);
		verify(repoManager).pullFromRepoUrl(eq("https://charts.example.com"), eq("mychart"), eq("1.0.0"), anyString(),
				auth.capture(), eq(false));
		RepositoryConfig.Repository built = auth.getValue();
		assertEquals("u", built.getUsername());
		assertEquals("p", built.getPassword());
		assertTrue(built.isInsecureSkipTlsVerify());
	}

	@Test
	void testPullUntarUnpacksAndRemovesArchive() throws IOException {
		stubPullWritesArchive("nginx-1.0.0.tgz", false);
		File untarDir = dest.resolve("unpacked").toFile();

		int exit = new CommandLine(pullCommand).execute("bitnami/nginx", "--version", "1.0.0", "--untar", "--untardir",
				untarDir.getPath(), "--dest", dest.toString());

		assertEquals(CommandLine.ExitCode.OK, exit);
		// The archive is unpacked into --untardir, not left as a .tgz in --dest.
		verify(repoManager).untar(any(File.class), eq(untarDir));
		assertTrue(untarDir.exists());
		assertTrue(!dest.resolve("nginx-1.0.0.tgz").toFile().exists());
	}

	@Test
	void testPullVerifyChecksProvenance() throws IOException {
		stubPullWritesArchive("nginx-1.0.0.tgz", true);

		int exit = new CommandLine(pullCommand).execute("bitnami/nginx", "--version", "1.0.0", "--verify", "--dest",
				dest.toString());

		assertEquals(CommandLine.ExitCode.OK, exit);
		// --verify fetches the .prov (withProv=true) and runs VerifyAction before
		// delivery.
		verify(repoManager).pull(eq("bitnami/nginx"), eq("1.0.0"), anyString(), eq(true));
		verify(verifyAction).verify(anyString(), anyString());
	}

	@Test
	void testPullProvKeepsProvenanceFile() throws IOException {
		stubPullWritesArchive("nginx-1.0.0.tgz", true);

		int exit = new CommandLine(pullCommand).execute("bitnami/nginx", "--version", "1.0.0", "--prov", "--dest",
				dest.toString());

		assertEquals(CommandLine.ExitCode.OK, exit);
		verify(repoManager).pull(eq("bitnami/nginx"), eq("1.0.0"), anyString(), eq(true));
		verify(verifyAction, never()).verify(anyString(), anyString());
		assertTrue(dest.resolve("nginx-1.0.0.tgz").toFile().exists());
		assertTrue(dest.resolve("nginx-1.0.0.tgz.prov").toFile().exists());
	}

	@Test
	void testPullWithDestOption() throws IOException {
		stubPullWritesArchive("nginx-1.0.0.tgz", false);

		int exit = new CommandLine(pullCommand).execute("oci://ghcr.io/helm/charts/nginx:1.0.0", "--dest",
				dest.toString());

		assertEquals(CommandLine.ExitCode.OK, exit);
		assertTrue(dest.resolve("nginx-1.0.0.tgz").toFile().exists());
	}

}
