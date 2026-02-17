package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.RepoManager;
import org.alexmond.jhelm.core.RepositoryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

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

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void testAddCommandSuccess() throws IOException {
        doNothing().when(repoManager).addRepo(anyString(), anyString());

        CommandLine cmd = new CommandLine(addCommand);
        cmd.execute("bitnami", "https://charts.bitnami.com/bitnami");
    }

    @Test
    void testAddCommandWithError() throws IOException {
        doThrow(new IOException("Test error")).when(repoManager).addRepo(anyString(), anyString());

        CommandLine cmd = new CommandLine(addCommand);
        cmd.execute("bitnami", "https://charts.bitnami.com/bitnami");
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
    void testRepoSearchCommand() {
        RepoCommand.SearchCommand searchCommand = new RepoCommand.SearchCommand(repoManager);
        CommandLine cmd = new CommandLine(searchCommand);
        try {
            cmd.execute("bitnami/nginx");
        } catch (Exception e) {
            // Expected - no repos configured
        }
    }

    @Test
    void testRepoSearchCommandWithKeyword() {
        RepoCommand.SearchCommand searchCommand = new RepoCommand.SearchCommand(repoManager);
        CommandLine cmd = new CommandLine(searchCommand);
        try {
            cmd.execute("database");
        } catch (Exception e) {
            // Expected - no repos configured
        }
    }

    @Test
    void testRepoSearchCommandWithVersionsFlag() throws Exception {
        RepoManager.ChartVersion result1 = new RepoManager.ChartVersion("bitnami/nginx", "1.0.0", "1.21.0", "Nginx web server");
        RepoManager.ChartVersion result2 = new RepoManager.ChartVersion("bitnami/nginx", "0.9.0", "1.20.0", "Nginx web server");

        when(repoManager.getChartVersions(anyString(), anyString()))
                .thenReturn(Arrays.asList(result1, result2));

        RepoCommand.SearchCommand searchCommand = new RepoCommand.SearchCommand(repoManager);
        CommandLine cmd = new CommandLine(searchCommand);
        int exitCode = cmd.execute("bitnami/nginx", "--versions");

        // Verify the command executed (covers the --versions branch)
        verify(repoManager).getChartVersions("bitnami", "nginx");
    }

    @Test
    void testRepoSearchCommandWithoutVersionsFlag() throws Exception {
        RepoManager.ChartVersion result = new RepoManager.ChartVersion("bitnami/nginx", "1.0.0", "1.21.0", "Nginx web server");

        when(repoManager.getChartVersions(anyString(), anyString()))
                .thenReturn(Arrays.asList(result));

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
        when(repoManager.getChartVersions(anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        RepoCommand.SearchCommand searchCommand = new RepoCommand.SearchCommand(repoManager);
        CommandLine cmd = new CommandLine(searchCommand);
        cmd.execute("bitnami/nonexistent");

        // Should log "No results found"
    }
}
