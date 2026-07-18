package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.app.plugin.HelmPostRendererResolver;
import org.alexmond.jhelm.core.config.JhelmCoreProperties;
import org.alexmond.jhelm.core.config.ConfigServerProperties;
import org.alexmond.jhelm.core.service.ConfigServerValuesLoader;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.action.DependencyUpdateAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.RollbackOptions;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.action.UpgradeOptions;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.alexmond.jhelm.core.exception.WaitTimeoutException;

import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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

	@Mock
	private DependencyUpdateAction dependencyUpdateAction;

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
		when(chartResolver.resolveFromRepo(anyString(), any(), any(), any(), anyBoolean(), any(), any()))
			.thenReturn(defaultChart);
		upgradeCommand = new UpgradeCommand(kubeService, installAction, uninstallAction, upgradeAction, rollbackAction,
				chartResolver, enabledPolicy(), new JhelmCoreProperties(),
				new ConfigServerValuesLoader(new ConfigServerProperties(), null), dependencyUpdateAction,
				HelmPostRendererResolver.fileOnly(enabledPolicy()));
	}

	private static JhelmSecurityPolicy enabledPolicy() {
		JhelmSecurityProperties props = new JhelmSecurityProperties();
		props.setMode(JhelmAccessMode.FULL);
		props.setApiKey("test-key");
		return new JhelmSecurityPolicy(props);
	}

	private static JhelmSecurityPolicy readOnlyPolicy() {
		return new JhelmSecurityPolicy(new JhelmSecurityProperties());
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
	void testUpgradeCommandOutputJson() throws Exception {
		File chartDir = createMockChart();
		when(kubeService.getRelease(anyString(), anyString()))
			.thenReturn(Optional.of(createMockRelease("my-release", 1)));
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenReturn(createMockRelease("my-release", 2));

		PrintStream originalOut = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
		int exitCode;
		try {
			exitCode = new CommandLine(upgradeCommand).execute("my-release", chartDir.getAbsolutePath(), "-o", "json");
		}
		finally {
			System.setOut(originalOut);
		}
		String out = captured.toString(StandardCharsets.UTF_8);
		assertEquals(CommandLine.ExitCode.OK, exitCode);
		assertTrue(out.contains("\"version\":2"), out);
		assertTrue(out.contains("\"info\":{"), out);
	}

	@Test
	void testUpgradeBlockedInReadOnlyMode() throws Exception {
		// #653: READ_ONLY (the default) must refuse a cluster-mutating upgrade and not
		// run it.
		File chartDir = createMockChart();
		UpgradeCommand readOnly = new UpgradeCommand(kubeService, installAction, uninstallAction, upgradeAction,
				rollbackAction, chartResolver, readOnlyPolicy(), new JhelmCoreProperties(),
				new ConfigServerValuesLoader(new ConfigServerProperties(), null), dependencyUpdateAction,
				HelmPostRendererResolver.fileOnly(enabledPolicy()));

		int exitCode = new CommandLine(readOnly).execute("my-release", chartDir.getAbsolutePath());

		assertNotEquals(CommandLine.ExitCode.OK, exitCode, "upgrade must be refused in READ_ONLY");
		verify(upgradeAction, never()).upgrade(any(UpgradeOptions.class));
	}

	@Test
	void testUpgradeWaitTimeoutExitsNonZero() throws Exception {
		// Regression for #647: an upgrade --wait readiness timeout must exit non-zero.
		File chartDir = createMockChart();
		Release existingRelease = createMockRelease("my-release", 1);
		Release upgradedRelease = createMockRelease("my-release", 2);
		when(kubeService.getRelease(anyString(), anyString())).thenReturn(Optional.of(existingRelease));
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenReturn(upgradedRelease);
		doThrow(new WaitTimeoutException("Timeout waiting for resources to be ready", List.of())).when(kubeService)
			.waitForReady(anyString(), anyString(), anyInt());

		int exitCode = new CommandLine(upgradeCommand).execute("my-release", chartDir.getAbsolutePath(), "--wait",
				"--timeout", "5");

		assertNotEquals(CommandLine.ExitCode.OK, exitCode, "an upgrade --wait timeout must exit non-zero");
	}

	@Test
	void testUpgradeWaitForJobsTriggersWait() throws Exception {
		File chartDir = createMockChart();
		when(kubeService.getRelease(anyString(), anyString()))
			.thenReturn(Optional.of(createMockRelease("my-release", 1)));
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenReturn(createMockRelease("my-release", 2));

		new CommandLine(upgradeCommand).execute("my-release", chartDir.getAbsolutePath(), "--wait-for-jobs");

		verify(kubeService).waitForReady(eq("default"), anyString(), anyInt());
	}

	@Test
	void testUpgradeDryRunServerModeIsDryRun() throws Exception {
		File chartDir = createMockChart();
		when(kubeService.getRelease(anyString(), anyString()))
			.thenReturn(Optional.of(createMockRelease("my-release", 1)));
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenReturn(createMockRelease("my-release", 2));
		ArgumentCaptor<UpgradeOptions> opts = ArgumentCaptor.forClass(UpgradeOptions.class);

		new CommandLine(upgradeCommand).execute("my-release", chartDir.getAbsolutePath(), "--dry-run=server");

		verify(upgradeAction).upgrade(opts.capture());
		assertTrue(opts.getValue().isDryRun());
		assertTrue(opts.getValue().isServerDryRun());
		verify(kubeService, never()).waitForReady(anyString(), anyString(), anyInt());
	}

	@Test
	void testUpgradeForceIsWiredIntoOptions() throws Exception {
		File chartDir = createMockChart();
		when(kubeService.getRelease(anyString(), anyString()))
			.thenReturn(Optional.of(createMockRelease("my-release", 1)));
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenReturn(createMockRelease("my-release", 2));
		ArgumentCaptor<UpgradeOptions> opts = ArgumentCaptor.forClass(UpgradeOptions.class);

		new CommandLine(upgradeCommand).execute("my-release", chartDir.getAbsolutePath(), "--force");

		verify(upgradeAction).upgrade(opts.capture());
		assertTrue(opts.getValue().isForce());
	}

	@Test
	void testDependencyUpdateTriggersUpdateOnLocalChartDir() throws Exception {
		File chartDir = createMockChart();
		when(kubeService.getRelease(anyString(), anyString()))
			.thenReturn(Optional.of(createMockRelease("my-release", 1)));
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenReturn(createMockRelease("my-release", 2));

		new CommandLine(upgradeCommand).execute("my-release", chartDir.getAbsolutePath(), "--dependency-update");

		verify(dependencyUpdateAction).update(any(File.class), any(), eq(false));
	}

	@Test
	void testUpgradeDescriptionAndLabelsAreWiredIntoOptions() throws Exception {
		File chartDir = createMockChart();
		when(kubeService.getRelease(anyString(), anyString()))
			.thenReturn(Optional.of(createMockRelease("my-release", 1)));
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenReturn(createMockRelease("my-release", 2));
		ArgumentCaptor<UpgradeOptions> opts = ArgumentCaptor.forClass(UpgradeOptions.class);

		new CommandLine(upgradeCommand).execute("my-release", chartDir.getAbsolutePath(), "--description", "rollout B",
				"--labels", "team=payments,tier=web");

		verify(upgradeAction).upgrade(opts.capture());
		assertEquals("rollout B", opts.getValue().getDescription());
		assertEquals("payments", opts.getValue().getLabels().get("team"));
		assertEquals("web", opts.getValue().getLabels().get("tier"));
	}

	@Test
	void testUpgradeInvalidDryRunModeIsHandled() throws Exception {
		File chartDir = createMockChart();

		new CommandLine(upgradeCommand).execute("my-release", chartDir.getAbsolutePath(), "--dry-run=bogus");

		// invalid mode is reported before any work; upgrade is never attempted
		verify(upgradeAction, never()).upgrade(any(UpgradeOptions.class));
	}

	@Test
	void testUpgradeDryRunNoneUpgradesForReal() throws Exception {
		File chartDir = createMockChart();
		when(kubeService.getRelease(anyString(), anyString()))
			.thenReturn(Optional.of(createMockRelease("my-release", 1)));
		when(upgradeAction.upgrade(any(UpgradeOptions.class))).thenReturn(createMockRelease("my-release", 2));
		ArgumentCaptor<UpgradeOptions> opts = ArgumentCaptor.forClass(UpgradeOptions.class);

		new CommandLine(upgradeCommand).execute("my-release", chartDir.getAbsolutePath(), "--dry-run=none", "--wait");

		verify(upgradeAction).upgrade(opts.capture());
		assertTrue(!opts.getValue().isDryRun());
		verify(kubeService).waitForReady(eq("default"), anyString(), anyInt());
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
