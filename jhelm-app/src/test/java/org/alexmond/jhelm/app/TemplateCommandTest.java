package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.TemplateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class TemplateCommandTest {

    @Mock
    private TemplateAction templateAction;

    private TemplateCommand templateCommand;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        templateCommand = new TemplateCommand(templateAction);
    }

    @Test
    void testTemplateCommandSuccess() throws Exception {
        when(templateAction.render(anyString(), anyString(), anyString()))
                .thenReturn("---\nkind: Service\n");

        CommandLine cmd = new CommandLine(templateCommand);
        cmd.execute("my-release", "/path/to/chart");
    }

    @Test
    void testTemplateCommandWithError() throws Exception {
        when(templateAction.render(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Test error"));

        CommandLine cmd = new CommandLine(templateCommand);
        cmd.execute("my-release", "/path/to/chart");
    }
}
