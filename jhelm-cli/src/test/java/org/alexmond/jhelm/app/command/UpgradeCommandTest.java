package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.RollbackOptions;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.action.UpgradeOptions;

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
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpgradeCommandTest {

	@Mock
	private KubeService kubeService;

	@Mock
	private InstallAction installAction;

	@Mock
	private UninstallAction uninstallAction;

	@Mock
	private UpgradeAction upgradeAction;

	@Mock
	private RollbackAction rollbackAction;

	@Mock
	private ChartResolver chartResolver;

	private UpgradeCommand upgradeCommand;

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);
		Chart defaultChart = Chart.builder()
			.metadata(ChartMetadata.builder().name("test-chart").version("1.0.0").build())
			.values(new HashMap<>())
			.build();
		when(chartResolver.resolve(anyString(), anyBoolean(), any())).thenReturn(defaultChart);
		upgradeCommand = new UpgradeCommand(kubeService, installAction, uninstallAction, upgradeAction, rollbackAction,
				chartResolver);
	}

	@Test
	void testUpgradeCommandWithExistingRelease() throws Exception {
		File chartDir = createMockChart();
		Release existingRelease = createMockRelease("my-release", 1);
		Release upgradedRelease = createMockRelease("my-release", 2);

		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(existingRelease));
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenReturn(upgradedRelease);

		CommandLine cmd = new CommandLine(upgradeCommand);
		cmd.execute("my-release", chartDir.getAbsolutePath(), "-n", "default");
	}

	@Test
	void testUpgradeCommandWithInstallFlag() throws Exception {
		File chartDir = createMockChart();
		Release newRelease = createMockRelease("my-release", 1);

		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.empty());
		when(installAction.install(any(InstallOptions.class))).thenReturn(newRelease);

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
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenReturn(upgradedRelease);

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

		when(kubeService.getRelease(anyString(), anyString())).thenThrow(new RuntimeException("Kubernetes error"));

		CommandLine cmd = new CommandLine(upgradeCommand);
		cmd.execute("my-release", chartDir.getAbsolutePath());
	}

	@Test
	void testUpgradeCommandWithWait() throws Exception {
		File chartDir = createMockChart();
		Release existingRelease = createMockRelease("my-release", 1);
		Release upgradedRelease = createMockRelease("my-release", 2);

		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(existingRelease));
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenReturn(upgradedRelease);

		CommandLine cmd = new CommandLine(upgradeCommand);
		cmd.execute("my-release", chartDir.getAbsolutePath(), "--wait", "--timeout", "120");

		verify(kubeService).waitForReady(eq("default"), anyString(), eq(120));
	}

	@Test
	void testUpgradeCommandWithNamespace() throws Exception {
		File chartDir = createMockChart();
		Release existingRelease = createMockRelease("my-release", 1);
		Release upgradedRelease = createMockRelease("my-release", 2);

		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(existingRelease));
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenReturn(upgradedRelease);

		CommandLine cmd = new CommandLine(upgradeCommand);
		cmd.execute("my-release", chartDir.getAbsolutePath(), "-n", "custom-namespace");

		verify(kubeService).getRelease("my-release", "custom-namespace");
	}

	@Test
	void testUpgradeCommandWithInstallAndDryRun() throws Exception {
		File chartDir = createMockChart();
		Release newRelease = createMockRelease("my-release", 1);

		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.empty());
		when(installAction.install(any(InstallOptions.class))).thenReturn(newRelease);

		CommandLine cmd = new CommandLine(upgradeCommand);
		cmd.execute("my-release", chartDir.getAbsolutePath(), "--install", "--dry-run");

		verify(installAction).install(argThat((InstallOptions options) -> "my-release".equals(options.getReleaseName())
				&& "default".equals(options.getNamespace()) && options.getRevision() == 1 && options.isDryRun()));
	}

	@Test
	void testUpgradeCommandAtomicRollsBackOnFailure() throws Exception {
		File chartDir = createMockChart();
		Release existingRelease = createMockRelease("my-release", 3);

		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(existingRelease));
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenThrow(new RuntimeException("upgrade failed"));

		CommandLine cmd = new CommandLine(upgradeCommand);
		cmd.execute("my-release", chartDir.getAbsolutePath(), "--atomic");

		verify(rollbackAction)
			.rollback(argThat((RollbackOptions options) -> "my-release".equals(options.getReleaseName())
					&& "default".equals(options.getNamespace()) && options.getRevision() == 3));
	}

	private Release createMockRelease(String name, int version) {
		ChartMetadata metadata = ChartMetadata.builder().name("test-chart").version("1.0.0").build();

		Chart chart = Chart.builder().metadata(metadata).build();

		Release.ReleaseInfo info = Release.ReleaseInfo.builder().status(ReleaseStatus.DEPLOYED).build();

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
