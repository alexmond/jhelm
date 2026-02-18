package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.CreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateCommandUnitTest {

    @Mock
    private CreateAction createAction;

    private CreateCommand createCommand;
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        createCommand = new CreateCommand(createAction);
        System.setOut(new PrintStream(outputStream));
    }

    @Test
    void testCreateCommandSuccess() throws Exception {
        doNothing().when(createAction).create(any());

        CommandLine cmd = new CommandLine(createCommand);
        int exitCode = cmd.execute("mychart");

        assertEquals(0, exitCode);
        verify(createAction).create(Paths.get("mychart"));
        assertTrue(outputStream.toString().contains("Creating mychart"));
    }

    @Test
    void testCreateCommandWithStarter() throws Exception {
        doNothing().when(createAction).create(any());

        CommandLine cmd = new CommandLine(createCommand);
        int exitCode = cmd.execute("--starter", "my-starter", "mychart-with-starter");

        assertEquals(0, exitCode);
        verify(createAction).create(Paths.get("mychart-with-starter"));
        assertTrue(outputStream.toString().contains("Creating mychart-with-starter"));
    }

    @Test
    void testCreateCommandConstructor() {
        assertNotNull(createCommand);
        CreateCommand cmd = new CreateCommand(createAction);
        assertNotNull(cmd);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }
}
