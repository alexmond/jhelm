package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.RegistryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

class RegistryCommandTest {

    @Mock
    private RegistryManager registryManager;

    private RegistryCommand.LoginCommand loginCommand;
    private RegistryCommand.LogoutCommand logoutCommand;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        loginCommand = new RegistryCommand.LoginCommand(registryManager);
        logoutCommand = new RegistryCommand.LogoutCommand(registryManager);
    }

    @Test
    void testLoginCommandSuccess() throws IOException {
        doNothing().when(registryManager).login(anyString(), anyString(), anyString());

        CommandLine cmd = new CommandLine(loginCommand);
        cmd.execute("registry.example.com", "-u", "user", "-p", "pass");
    }

    @Test
    void testLoginCommandWithError() throws IOException {
        doThrow(new IOException("Test error")).when(registryManager).login(anyString(), anyString(), anyString());

        CommandLine cmd = new CommandLine(loginCommand);
        cmd.execute("registry.example.com", "-u", "user", "-p", "pass");
    }

    @Test
    void testLogoutCommandSuccess() throws IOException {
        doNothing().when(registryManager).logout(anyString());

        CommandLine cmd = new CommandLine(logoutCommand);
        cmd.execute("registry.example.com");
    }

    @Test
    void testLogoutCommandWithError() throws IOException {
        doThrow(new IOException("Test error")).when(registryManager).logout(anyString());

        CommandLine cmd = new CommandLine(logoutCommand);
        cmd.execute("registry.example.com");
    }

    @Test
    void testRegistryCommandShowsUsage() {
        RegistryCommand registryCommand = new RegistryCommand();
        CommandLine cmd = new CommandLine(registryCommand);
        cmd.execute();
    }
}
