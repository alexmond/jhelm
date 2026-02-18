package org.alexmond.jhelm.app;

import org.alexmond.jhelm.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class InstallCommandTest {

    @Mock
    private InstallAction installAction;

    private InstallCommand installCommand;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        installCommand = new InstallCommand(installAction);
    }

    @Test
    void testInstallCommandSuccess() throws Exception {
        File chartDir = createMockChart();
        Release release = createMockRelease("my-release", 1);

        when(installAction.install(any(Chart.class), anyString(), anyString(), anyMap(), anyInt(), anyBoolean()))
                .thenReturn(release);

        CommandLine cmd = new CommandLine(installCommand);
        cmd.execute("my-release", chartDir.getAbsolutePath(), "-n", "default");
    }

    @Test
    void testInstallCommandWithDryRun() throws Exception {
        File chartDir = createMockChart();
        Release release = createMockRelease("my-release", 1);

        when(installAction.install(any(Chart.class), anyString(), anyString(), anyMap(), anyInt(), anyBoolean()))
                .thenReturn(release);

        CommandLine cmd = new CommandLine(installCommand);
        cmd.execute("my-release", chartDir.getAbsolutePath(), "--dry-run");
    }

    @Test
    void testInstallCommandWithError() throws Exception {
        File chartDir = createMockChart();

        when(installAction.install(any(Chart.class), anyString(), anyString(), anyMap(), anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("Test error"));

        CommandLine cmd = new CommandLine(installCommand);
        cmd.execute("my-release", chartDir.getAbsolutePath());
    }

    private File createMockChart() throws Exception {
        File chartDir = tempDir.resolve("test-chart").toFile();
        chartDir.mkdirs();

        String chartYaml = """
                apiVersion: v2
                name: test-chart
                version: 1.0.0
                """;
        Files.writeString(chartDir.toPath().resolve("Chart.yaml"), chartYaml);

        return chartDir;
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
                .manifest("---\nkind: Service\n")
                .build();
    }
}
