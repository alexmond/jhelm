package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.RollbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

class RollbackCommandTest {

    @Mock
    private RollbackAction rollbackAction;

    private RollbackCommand rollbackCommand;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        rollbackCommand = new RollbackCommand(rollbackAction);
    }

    @Test
    void testRollbackCommandSuccess() throws Exception {
        doNothing().when(rollbackAction).rollback(anyString(), anyString(), anyInt());

        CommandLine cmd = new CommandLine(rollbackCommand);
        cmd.execute("my-release", "1", "-n", "default");
    }

    @Test
    void testRollbackCommandDefaultNamespace() throws Exception {
        doNothing().when(rollbackAction).rollback(anyString(), anyString(), anyInt());

        CommandLine cmd = new CommandLine(rollbackCommand);
        cmd.execute("my-release", "2");
    }

    @Test
    void testRollbackCommandWithError() throws Exception {
        doThrow(new RuntimeException("Test error")).when(rollbackAction).rollback(anyString(), anyString(), anyInt());

        CommandLine cmd = new CommandLine(rollbackCommand);
        cmd.execute("my-release", "1");
    }
}
