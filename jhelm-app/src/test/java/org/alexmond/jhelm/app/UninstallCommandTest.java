package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.UninstallAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

class UninstallCommandTest {

    @Mock
    private UninstallAction uninstallAction;

    private UninstallCommand uninstallCommand;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        uninstallCommand = new UninstallCommand(uninstallAction);
    }

    @Test
    void testUninstallCommandSuccess() throws Exception {
        doNothing().when(uninstallAction).uninstall(anyString(), anyString());

        CommandLine cmd = new CommandLine(uninstallCommand);
        cmd.execute("my-release", "-n", "default");
    }

    @Test
    void testUninstallCommandDefaultNamespace() throws Exception {
        doNothing().when(uninstallAction).uninstall(anyString(), anyString());

        CommandLine cmd = new CommandLine(uninstallCommand);
        cmd.execute("my-release");
    }

    @Test
    void testUninstallCommandWithError() throws Exception {
        doThrow(new RuntimeException("Test error")).when(uninstallAction).uninstall(anyString(), anyString());

        CommandLine cmd = new CommandLine(uninstallCommand);
        cmd.execute("my-release");
    }
}
