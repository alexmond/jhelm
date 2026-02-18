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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpgradeCommandTest {

    @Mock
    private KubeService kubeService;

    @Mock
    private InstallAction installAction;

    @Mock
    private UpgradeAction upgradeAction;

    private UpgradeCommand upgradeCommand;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        upgradeCommand = new UpgradeCommand(kubeService, installAction, upgradeAction);
    }

    @Test
    void testUpgradeCommandWithExistingRelease() throws Exception {
        File chartDir = createMockChart();
        Release existingRelease = createMockRelease("my-release", 1);
        Release upgradedRelease = createMockRelease("my-release", 2);

        when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(existingRelease));
        when(upgradeAction.upgrade(any(Release.class), any(Chart.class), anyMap(), anyBoolean()))
                .thenReturn(upgradedRelease);

        CommandLine cmd = new CommandLine(upgradeCommand);
        cmd.execute("my-release", chartDir.getAbsolutePath(), "-n", "default");
    }

    @Test
    void testUpgradeCommandWithInstallFlag() throws Exception {
        File chartDir = createMockChart();
        Release newRelease = createMockRelease("my-release", 1);

        when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.empty());
        when(installAction.install(any(Chart.class), anyString(), anyString(), anyMap(), anyInt(), anyBoolean()))
                .thenReturn(newRelease);

        CommandLine cmd = new CommandLine(upgradeCommand);
        cmd.execute("my-release", chartDir.getAbsolutePath(), "--install");
    }

    @Test
    void testUpgradeCommandWithoutInstallFlag() throws Exception {
        File chartDir = createMockChart();

        when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.empty());

        CommandLine cmd = new CommandLine(upgradeCommand);
        cmd.execute("my-release", chartDir.getAbsolutePath());
    }

    @Test
    void testUpgradeCommandWithDryRun() throws Exception {
        File chartDir = createMockChart();
        Release existingRelease = createMockRelease("my-release", 1);
        Release upgradedRelease = createMockRelease("my-release", 2);

        when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(existingRelease));
        when(upgradeAction.upgrade(any(Release.class), any(Chart.class), anyMap(), anyBoolean()))
                .thenReturn(upgradedRelease);

        CommandLine cmd = new CommandLine(upgradeCommand);
        cmd.execute("my-release", chartDir.getAbsolutePath(), "--dry-run");
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

    @Test
    void testUpgradeCommandWithError() throws Exception {
        File chartDir = createMockChart();

        when(kubeService.getRelease(anyString(), anyString()))
                .thenThrow(new RuntimeException("Kubernetes error"));

        CommandLine cmd = new CommandLine(upgradeCommand);
        cmd.execute("my-release", chartDir.getAbsolutePath());
    }

    @Test
    void testUpgradeCommandWithTimeout() throws Exception {
        File chartDir = createMockChart();
        Release existingRelease = createMockRelease("my-release", 1);
        Release upgradedRelease = createMockRelease("my-release", 2);

        when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(existingRelease));
        when(upgradeAction.upgrade(any(Release.class), any(Chart.class), anyMap(), anyBoolean()))
                .thenReturn(upgradedRelease);

        CommandLine cmd = new CommandLine(upgradeCommand);
        cmd.execute("my-release", chartDir.getAbsolutePath(), "--timeout", "5m");
    }

    @Test
    void testUpgradeCommandWithNamespace() throws Exception {
        File chartDir = createMockChart();
        Release existingRelease = createMockRelease("my-release", 1);
        Release upgradedRelease = createMockRelease("my-release", 2);

        when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(existingRelease));
        when(upgradeAction.upgrade(any(Release.class), any(Chart.class), anyMap(), anyBoolean()))
                .thenReturn(upgradedRelease);

        CommandLine cmd = new CommandLine(upgradeCommand);
        cmd.execute("my-release", chartDir.getAbsolutePath(), "-n", "custom-namespace");

        verify(kubeService).getRelease("my-release", "custom-namespace");
    }

    @Test
    void testUpgradeCommandWithInstallAndDryRun() throws Exception {
        File chartDir = createMockChart();
        Release newRelease = createMockRelease("my-release", 1);

        when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.empty());
        when(installAction.install(any(Chart.class), anyString(), anyString(), anyMap(), anyInt(), anyBoolean()))
                .thenReturn(newRelease);

        CommandLine cmd = new CommandLine(upgradeCommand);
        cmd.execute("my-release", chartDir.getAbsolutePath(), "--install", "--dry-run");

        verify(installAction).install(any(Chart.class), eq("my-release"), eq("default"), anyMap(), eq(1), eq(true));
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
