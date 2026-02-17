package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class HistoryCommandTest {

    @Mock
    private HistoryAction historyAction;

    private HistoryCommand historyCommand;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        historyCommand = new HistoryCommand(historyAction);
    }

    @Test
    void testHistoryCommandWithReleases() throws Exception {
        when(historyAction.history(anyString(), anyString())).thenReturn(Arrays.asList(
                createMockRelease("my-release", 1, "Initial install"),
                createMockRelease("my-release", 2, "Upgrade")
        ));

        CommandLine cmd = new CommandLine(historyCommand);
        cmd.execute("my-release", "-n", "default");
    }

    @Test
    void testHistoryCommandWithNoHistory() throws Exception {
        when(historyAction.history(anyString(), anyString())).thenReturn(Collections.emptyList());

        CommandLine cmd = new CommandLine(historyCommand);
        cmd.execute("my-release");
    }

    @Test
    void testHistoryCommandWithError() throws Exception {
        when(historyAction.history(anyString(), anyString())).thenThrow(new RuntimeException("Test error"));

        CommandLine cmd = new CommandLine(historyCommand);
        cmd.execute("my-release");
    }

    private Release createMockRelease(String name, int version, String description) {
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
                .description(description)
                .build();

        return Release.builder()
                .name(name)
                .namespace("default")
                .version(version)
                .chart(chart)
                .info(info)
                .build();
    }
}
