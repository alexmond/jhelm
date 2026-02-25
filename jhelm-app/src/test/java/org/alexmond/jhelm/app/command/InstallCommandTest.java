package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.action.InstallAction;

import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstallCommandTest {

	@Mock
	private InstallAction installAction;

	@Mock
	private KubeService kubeService;

	@Mock
	private ChartLoader chartLoader;

	private InstallCommand installCommand;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);
		Chart defaultChart = Chart.builder()
			.metadata(ChartMetadata.builder().name("test-chart").version("1.0.0").build())
			.values(new HashMap<>())
			.build();
		when(chartLoader.load(any(File.class))).thenReturn(defaultChart);
		installCommand = new InstallCommand(installAction, kubeService, chartLoader);
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

	@Test
	void testInstallCommandWithWait() throws Exception {
		File chartDir = createMockChart();
		Release release = createMockRelease("my-release", 1);

		when(installAction.install(any(Chart.class), anyString(), anyString(), anyMap(), anyInt(), anyBoolean()))
			.thenReturn(release);

		CommandLine cmd = new CommandLine(installCommand);
		cmd.execute("my-release", chartDir.getAbsolutePath(), "--wait", "--timeout", "120");

		verify(kubeService).waitForReady(eq("default"), anyString(), eq(120));
	}

	@Test
	void testInstallCommandWithWaitDoesNotCallOnDryRun() throws Exception {
		File chartDir = createMockChart();
		Release release = createMockRelease("my-release", 1);

		when(installAction.install(any(Chart.class), anyString(), anyString(), anyMap(), anyInt(), anyBoolean()))
			.thenReturn(release);

		CommandLine cmd = new CommandLine(installCommand);
		cmd.execute("my-release", chartDir.getAbsolutePath(), "--wait", "--dry-run");

		// waitForReady should NOT be called when --dry-run is active
		verify(kubeService, org.mockito.Mockito.never()).waitForReady(anyString(), anyString(), anyInt());
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
		ChartMetadata metadata = ChartMetadata.builder().name("test-chart").version("1.0.0").build();

		Chart chart = Chart.builder().metadata(metadata).build();

		Release.ReleaseInfo info = Release.ReleaseInfo.builder().status("deployed").build();

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
