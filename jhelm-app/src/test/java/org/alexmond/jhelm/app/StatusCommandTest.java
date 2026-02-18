package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class StatusCommandTest {

    @Mock
    private StatusAction statusAction;

    private StatusCommand statusCommand;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        statusCommand = new StatusCommand(statusAction);
    }

    @Test
    void testStatusCommandWithExistingRelease() throws Exception {
        Release release = createMockRelease();
        when(statusAction.status(anyString(), anyString())).thenReturn(Optional.of(release));

        CommandLine cmd = new CommandLine(statusCommand);
        cmd.execute("my-release", "-n", "default");
    }

    @Test
    void testStatusCommandWithNonExistingRelease() throws Exception {
        when(statusAction.status(anyString(), anyString())).thenReturn(Optional.empty());

        CommandLine cmd = new CommandLine(statusCommand);
        cmd.execute("non-existing-release");
    }

    @Test
    void testStatusCommandWithError() throws Exception {
        when(statusAction.status(anyString(), anyString())).thenThrow(new RuntimeException("Test error"));

        CommandLine cmd = new CommandLine(statusCommand);
        cmd.execute("my-release");
    }

    private Release createMockRelease() {
        ChartMetadata metadata = ChartMetadata.builder()
                .name("test-chart")
                .version("1.0.0")
                .build();

        Chart chart = Chart.builder()
                .metadata(metadata)
                .build();

        Release.ReleaseInfo info = Release.ReleaseInfo.builder()
                .status("deployed")
                .lastDeployed(OffsetDateTime.now())
                .build();

        return Release.builder()
                .name("my-release")
                .namespace("default")
                .version(1)
                .chart(chart)
                .info(info)
                .manifest("---\nkind: Service\n")
                .build();
    }
}
