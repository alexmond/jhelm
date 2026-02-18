package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ListCommandTest {

    @Mock
    private ListAction listAction;

    private ListCommand listCommand;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listCommand = new ListCommand(listAction);
    }

    @Test
    void testListCommandWithReleases() throws Exception {
        when(listAction.list(anyString())).thenReturn(Arrays.asList(
                createMockRelease("release1", 1),
                createMockRelease("release2", 2)
        ));

        CommandLine cmd = new CommandLine(listCommand);
        cmd.execute("-n", "default");
    }

    @Test
    void testListCommandWithNoReleases() throws Exception {
        when(listAction.list(anyString())).thenReturn(Collections.emptyList());

        CommandLine cmd = new CommandLine(listCommand);
        cmd.execute();
    }

    @Test
    void testListCommandWithError() throws Exception {
        when(listAction.list(anyString())).thenThrow(new RuntimeException("Test error"));

        CommandLine cmd = new CommandLine(listCommand);
        cmd.execute();
    }

    private Release createMockRelease(String name, int version) {
        ChartMetadata metadata = ChartMetadata.builder()
                .name("test-chart")
                .version("1.0.0")
                .build();

        Chart chart = Chart.builder()
                .metadata(metadata)
                .build();

        Release.ReleaseInfo info = Release.ReleaseInfo.builder()
                .status("deployed")
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
