package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.model.RepositoryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.anyString;
import org.junit.jupiter.api.AfterEach;

class RepoCommandTest {

	@Mock
	private RepoManager repoManager;

	private RepoCommand.AddCommand addCommand;

	private RepoCommand.ListCommand listCommand;

	private RepoCommand.RemoveCommand removeCommand;

	private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

	private final PrintStream originalOut = System.out;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		addCommand = new RepoCommand.AddCommand(repoManager);
		listCommand = new RepoCommand.ListCommand(repoManager);
		removeCommand = new RepoCommand.RemoveCommand(repoManager);
		System.setOut(new PrintStream(outputStream));
	}

	@AfterEach
	void tearDown() {
		System.setOut(originalOut);
	}

	@Test
	void testAddCommandSuccess() throws IOException {
		int exit = new CommandLine(addCommand).execute("bitnami", "https://charts.bitnami.com/bitnami");

		assertEquals(CommandLine.ExitCode.OK, exit);
		ArgumentCaptor<RepositoryConfig.Repository> captor = ArgumentCaptor.forClass(RepositoryConfig.Repository.class);
		verify(repoManager).addRepo(captor.capture(), eq(true), eq(false));
		assertEquals("bitnami", captor.getValue().getName());
		assertEquals("https://charts.bitnami.com/bitnami", captor.getValue().getUrl());
	}

	@Test
	void testAddCommandWithAuthTlsAndFlags() throws IOException {
		int exit = new CommandLine(addCommand).execute("private", "https://charts.example.com", "--username", "u",
				"--password", "p", "--ca-file", "/ca.pem", "--insecure-skip-tls-verify", "--pass-credentials",
				"--force-update", "--no-update");

		assertEquals(CommandLine.ExitCode.OK, exit);
		ArgumentCaptor<RepositoryConfig.Repository> captor = ArgumentCaptor.forClass(RepositoryConfig.Repository.class);
		// --no-update -> update=false, --force-update -> forceUpdate=true
		verify(repoManager).addRepo(captor.capture(), eq(false), eq(true));
		RepositoryConfig.Repository repo = captor.getValue();
		assertEquals("u", repo.getUsername());
		assertEquals("p", repo.getPassword());
		assertEquals("/ca.pem", repo.getCaFile());
		assertTrue(repo.isInsecureSkipTlsVerify());
		assertTrue(repo.isPassCredentialsAll());
	}

	@Test
	void testAddCommandWithError() throws IOException {
		doThrow(new IOException("Test error")).when(repoManager)
			.addRepo(any(RepositoryConfig.Repository.class), anyBoolean(), anyBoolean());

		int exit = new CommandLine(addCommand).execute("bitnami", "https://charts.bitnami.com/bitnami");

		assertEquals(CommandLine.ExitCode.SOFTWARE, exit);
	}

	@Test
	void testListCommandSuccess() throws IOException {
		RepositoryConfig config = new RepositoryConfig();
		RepositoryConfig.Repository repo1 = new RepositoryConfig.Repository();
		repo1.setName("bitnami");
		repo1.setUrl("https://charts.bitnami.com/bitnami");

		RepositoryConfig.Repository repo2 = new RepositoryConfig.Repository();
		repo2.setName("stable");
		repo2.setUrl("https://charts.helm.sh/stable");

		config.setRepositories(Arrays.asList(repo1, repo2));

		when(repoManager.loadConfig()).thenReturn(config);

		CommandLine cmd = new CommandLine(listCommand);
		cmd.execute();

		String output = outputStream.toString();
		assertTrue(output.contains("NAME"));
		assertTrue(output.contains("URL"));
		assertTrue(output.contains("bitnami"));
		assertTrue(output.contains("stable"));
	}

	@Test
	void testListCommandOutputJson() throws IOException {
		RepositoryConfig config = new RepositoryConfig();
		RepositoryConfig.Repository repo1 = new RepositoryConfig.Repository();
		repo1.setName("bitnami");
		repo1.setUrl("https://charts.bitnami.com/bitnami");
		config.setRepositories(Arrays.asList(repo1));
		when(repoManager.loadConfig()).thenReturn(config);

		int exit = new CommandLine(listCommand).execute("-o", "json");
		String output = outputStream.toString().strip();
		assertEquals(0, exit);
		assertTrue(output.startsWith("[") && output.endsWith("]"), output);
		assertTrue(output.contains("\"name\":\"bitnami\""), output);
		assertTrue(output.contains("\"url\":\"https://charts.bitnami.com/bitnami\""), output);
	}

	@Test
	void testListCommandWithEmptyRepos() throws IOException {
		RepositoryConfig config = new RepositoryConfig();
		config.setRepositories(new ArrayList<>());

		when(repoManager.loadConfig()).thenReturn(config);

		CommandLine cmd = new CommandLine(listCommand);
		cmd.execute();

		String output = outputStream.toString();
		assertTrue(output.contains("NAME"));
	}

	@Test
	void testListCommandWithError() throws IOException {
		when(repoManager.loadConfig()).thenThrow(new IOException("Test error"));

		CommandLine cmd = new CommandLine(listCommand);
		cmd.execute();
	}

	@Test
	void testRemoveCommandSuccess() throws IOException {
		doNothing().when(repoManager).removeRepo(anyString());

		CommandLine cmd = new CommandLine(removeCommand);
		cmd.execute("bitnami");
	}

	@Test
	void testRemoveCommandWithError() throws IOException {
		doThrow(new IOException("Test error")).when(repoManager).removeRepo(anyString());

		CommandLine cmd = new CommandLine(removeCommand);
		cmd.execute("bitnami");
	}

	@Test
	void testRepoCommandShowsUsage() {
		RepoCommand repoCommand = new RepoCommand();
		CommandLine cmd = new CommandLine(repoCommand);
		cmd.execute();
	}

	@Test
	void testRepoUpdateAllSuccess() throws IOException {
		doNothing().when(repoManager).updateAll();

		new CommandLine(new RepoCommand.UpdateCommand(repoManager)).execute();

		verify(repoManager).updateAll();
		assertTrue(outputStream.toString().contains("Update Complete"));
	}

	@Test
	void testRepoUpdateNamedRepo() throws IOException {
		doNothing().when(repoManager).updateRepo(anyString());

		new CommandLine(new RepoCommand.UpdateCommand(repoManager)).execute("bitnami");

		verify(repoManager).updateRepo("bitnami");
	}

	@Test
	void testRepoUpdateWithError() throws IOException {
		doThrow(new IOException("Test error")).when(repoManager).updateAll();

		// covers the error branch; error goes to stderr and is handled without throwing
		new CommandLine(new RepoCommand.UpdateCommand(repoManager)).execute();

		verify(repoManager).updateAll();
	}

	@Test
	void testRepoSearchCommand() {
		RepoCommand.SearchCommand searchCommand = new RepoCommand.SearchCommand(repoManager);
		CommandLine cmd = new CommandLine(searchCommand);
		try {
			cmd.execute("bitnami/nginx");
		}
		catch (Exception ex) {
			// Expected - no repos configured
		}
	}

	@Test
	void testRepoSearchCommandWithKeyword() {
		RepoCommand.SearchCommand searchCommand = new RepoCommand.SearchCommand(repoManager);
		CommandLine cmd = new CommandLine(searchCommand);
		try {
			cmd.execute("database");
		}
		catch (Exception ex) {
			// Expected - no repos configured
		}
	}

	@Test
	void testRepoSearchCommandWithVersionsFlag() throws Exception {
		RepoManager.ChartVersion result1 = new RepoManager.ChartVersion("bitnami/nginx", "1.0.0", "1.21.0",
				"Nginx web server");
		RepoManager.ChartVersion result2 = new RepoManager.ChartVersion("bitnami/nginx", "0.9.0", "1.20.0",
				"Nginx web server");

		when(repoManager.getChartVersions(anyString(), anyString())).thenReturn(Arrays.asList(result1, result2));

		RepoCommand.SearchCommand searchCommand = new RepoCommand.SearchCommand(repoManager);
		CommandLine cmd = new CommandLine(searchCommand);
		int exitCode = cmd.execute("bitnami/nginx", "--versions");

		// Verify the command executed (covers the --versions branch)
		verify(repoManager).getChartVersions("bitnami", "nginx");
	}

	@Test
	void testRepoSearchCommandWithoutVersionsFlag() throws Exception {
		RepoManager.ChartVersion result = new RepoManager.ChartVersion("bitnami/nginx", "1.0.0", "1.21.0",
				"Nginx web server");

		when(repoManager.getChartVersions(anyString(), anyString())).thenReturn(Arrays.asList(result));

		RepoCommand.SearchCommand searchCommand = new RepoCommand.SearchCommand(repoManager);
		CommandLine cmd = new CommandLine(searchCommand);
		int exitCode = cmd.execute("bitnami/nginx");

		// Verify the command executed (covers the default branch)
		verify(repoManager).getChartVersions("bitnami", "nginx");
	}

	@Test
	void testRepoSearchCommandInvalidQuery() {
		RepoCommand.SearchCommand searchCommand = new RepoCommand.SearchCommand(repoManager);
		CommandLine cmd = new CommandLine(searchCommand);
		cmd.execute("invalid-query-no-slash");

		// Should log error about format
	}

	@Test
	void testRepoSearchCommandNoResults() throws Exception {
		when(repoManager.getChartVersions(anyString(), anyString())).thenReturn(new ArrayList<>());

		RepoCommand.SearchCommand searchCommand = new RepoCommand.SearchCommand(repoManager);
		CommandLine cmd = new CommandLine(searchCommand);
		cmd.execute("bitnami/nonexistent");

		// Should log "No results found"
	}

}
