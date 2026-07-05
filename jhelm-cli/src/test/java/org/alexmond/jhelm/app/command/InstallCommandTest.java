package org.alexmond.jhelm.app.command;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.alexmond.jhelm.core.config.JhelmCoreProperties;
import org.alexmond.jhelm.core.config.ConfigServerProperties;
import org.alexmond.jhelm.core.service.ConfigServerValuesLoader;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.alexmond.jhelm.core.exception.WaitTimeoutException;

import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class InstallCommandTest {

	@Mock
	private InstallAction installAction;

	@Mock
	private UninstallAction uninstallAction;

	@Mock
	private KubeService kubeService;

	@Mock
	private ChartResolver chartResolver;

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
		when(chartResolver.resolve(anyString(), anyBoolean(), any())).thenReturn(defaultChart);
		installCommand = new InstallCommand(installAction, uninstallAction, kubeService, chartResolver, enabledPolicy(),
				new JhelmCoreProperties(), new ConfigServerValuesLoader(new ConfigServerProperties(), null));
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
	void testInstallCommandSuccess() throws Exception {
		File chartDir = createMockChart();
		Release release = createMockRelease("my-release", 1);

		when(installAction.install(any(InstallOptions.class))).thenReturn(release);

		CommandLine cmd = new CommandLine(installCommand);
		int exitCode = cmd.execute("my-release", chartDir.getAbsolutePath(), "-n", "default");

		assertEquals(CommandLine.ExitCode.OK, exitCode);
	}

	@Test
	void testInstallCommandOutputJson() throws Exception {
		File chartDir = createMockChart();
		when(installAction.install(any(InstallOptions.class))).thenReturn(createMockRelease("my-release", 1));

		PrintStream originalOut = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
		int exitCode;
		try {
			exitCode = new CommandLine(installCommand).execute("my-release", chartDir.getAbsolutePath(), "-o", "json");
		}
		finally {
			System.setOut(originalOut);
		}
		String out = captured.toString(StandardCharsets.UTF_8);
		assertEquals(CommandLine.ExitCode.OK, exitCode);
		assertTrue(out.contains("\"name\":\"my-release\""), out);
		assertTrue(out.contains("\"info\":{"), out);
	}

	@Test
	void testInstallCommandWithDryRun() throws Exception {
		File chartDir = createMockChart();
		Release release = createMockRelease("my-release", 1);

		when(installAction.install(any(InstallOptions.class))).thenReturn(release);

		CommandLine cmd = new CommandLine(installCommand);
		cmd.execute("my-release", chartDir.getAbsolutePath(), "--dry-run");
	}

	@Test
	void testInstallCommandWithError() throws Exception {
		File chartDir = createMockChart();

		when(installAction.install(any(InstallOptions.class))).thenThrow(new RuntimeException("Test error"));

		CommandLine cmd = new CommandLine(installCommand);
		int exitCode = cmd.execute("my-release", chartDir.getAbsolutePath());

		assertNotEquals(CommandLine.ExitCode.OK, exitCode, "a failed install must exit non-zero");
	}

	@Test
	void testInstallBlockedInReadOnlyMode() throws Exception {
		// #653: READ_ONLY (the default) must refuse a cluster-mutating install and not
		// run it.
		File chartDir = createMockChart();
		InstallCommand readOnly = new InstallCommand(installAction, uninstallAction, kubeService, chartResolver,
				readOnlyPolicy(), new JhelmCoreProperties(),
				new ConfigServerValuesLoader(new ConfigServerProperties(), null));

		int exitCode = new CommandLine(readOnly).execute("my-release", chartDir.getAbsolutePath());

		assertNotEquals(CommandLine.ExitCode.OK, exitCode, "install must be refused in READ_ONLY");
		verify(installAction, never()).install(any(InstallOptions.class));
	}

	@Test
	void testInstallAllowedInFullModeWithoutApiKey() throws Exception {
		// #657: the standalone CLI unlocks mutations with mode=FULL alone — no api-key
		// (the kubeconfig is the trust boundary, like helm). REST/MCP still require a
		// key.
		JhelmSecurityProperties props = new JhelmSecurityProperties();
		props.setMode(JhelmAccessMode.FULL);
		File chartDir = createMockChart();
		when(installAction.install(any(InstallOptions.class))).thenReturn(createMockRelease("my-release", 1));
		InstallCommand full = new InstallCommand(installAction, uninstallAction, kubeService, chartResolver,
				new JhelmSecurityPolicy(props), new JhelmCoreProperties(),
				new ConfigServerValuesLoader(new ConfigServerProperties(), null));

		int exitCode = new CommandLine(full).execute("my-release", chartDir.getAbsolutePath(), "-n", "default");

		assertEquals(CommandLine.ExitCode.OK, exitCode, "FULL mode must enable install without an api-key");
		verify(installAction).install(any(InstallOptions.class));
	}

	@Test
	void testInstallWaitTimeoutExitsNonZero() throws Exception {
		// Regression for #647: a --wait readiness timeout printed an error but exited 0,
		// so callers could not detect the failed rollout. It must now exit non-zero.
		File chartDir = createMockChart();
		Release release = createMockRelease("my-release", 1);
		when(installAction.install(any(InstallOptions.class))).thenReturn(release);
		Mockito.doThrow(new WaitTimeoutException("Timeout waiting for resources to be ready", List.of()))
			.when(kubeService)
			.waitForReady(anyString(), anyString(), anyInt());

		int exitCode = new CommandLine(installCommand).execute("my-release", chartDir.getAbsolutePath(), "--wait",
				"--timeout", "5");

		assertNotEquals(CommandLine.ExitCode.OK, exitCode, "a --wait timeout must exit non-zero");
	}

	@Test
	void testInstallCommandWithWait() throws Exception {
		File chartDir = createMockChart();
		Release release = createMockRelease("my-release", 1);

		when(installAction.install(any(InstallOptions.class))).thenReturn(release);

		CommandLine cmd = new CommandLine(installCommand);
		cmd.execute("my-release", chartDir.getAbsolutePath(), "--wait", "--timeout", "120");

		verify(kubeService).waitForReady(eq("default"), anyString(), eq(120));
	}

	@Test
	void testInstallCommandWithWaitDoesNotCallOnDryRun() throws Exception {
		File chartDir = createMockChart();
		Release release = createMockRelease("my-release", 1);

		when(installAction.install(any(InstallOptions.class))).thenReturn(release);

		CommandLine cmd = new CommandLine(installCommand);
		cmd.execute("my-release", chartDir.getAbsolutePath(), "--wait", "--dry-run");

		// waitForReady should NOT be called when --dry-run is active
		verify(kubeService, Mockito.never()).waitForReady(anyString(), anyString(), anyInt());
	}

	@Test
	void testInstallDryRunClientModeEnablesDryRun() throws Exception {
		File chartDir = createMockChart();
		when(installAction.install(any(InstallOptions.class))).thenReturn(createMockRelease("my-release", 1));
		ArgumentCaptor<InstallOptions> opts = ArgumentCaptor.forClass(InstallOptions.class);

		new CommandLine(installCommand).execute("my-release", chartDir.getAbsolutePath(), "--dry-run=client");

		verify(installAction).install(opts.capture());
		assertTrue(opts.getValue().isDryRun());
		verify(kubeService, never()).waitForReady(anyString(), anyString(), anyInt());
	}

	@Test
	void testInstallDryRunNoneModeInstallsForReal() throws Exception {
		File chartDir = createMockChart();
		when(installAction.install(any(InstallOptions.class))).thenReturn(createMockRelease("my-release", 1));
		ArgumentCaptor<InstallOptions> opts = ArgumentCaptor.forClass(InstallOptions.class);

		new CommandLine(installCommand).execute("my-release", chartDir.getAbsolutePath(), "--dry-run=none", "--wait");

		verify(installAction).install(opts.capture());
		assertFalse(opts.getValue().isDryRun());
		verify(kubeService).waitForReady(eq("default"), anyString(), anyInt());
	}

	@Test
	void testInstallDryRunServerModeIsDryRun() throws Exception {
		File chartDir = createMockChart();
		when(installAction.install(any(InstallOptions.class))).thenReturn(createMockRelease("my-release", 1));
		ArgumentCaptor<InstallOptions> opts = ArgumentCaptor.forClass(InstallOptions.class);

		new CommandLine(installCommand).execute("my-release", chartDir.getAbsolutePath(), "--dry-run=server");

		verify(installAction).install(opts.capture());
		assertTrue(opts.getValue().isDryRun());
		assertTrue(opts.getValue().isServerDryRun());
	}

	@Test
	void testInstallWaitForJobsTriggersWait() throws Exception {
		File chartDir = createMockChart();
		when(installAction.install(any(InstallOptions.class))).thenReturn(createMockRelease("my-release", 1));

		new CommandLine(installCommand).execute("my-release", chartDir.getAbsolutePath(), "--wait-for-jobs");

		verify(kubeService).waitForReady(eq("default"), anyString(), anyInt());
	}

	@Test
	void testInstallInvalidDryRunModeIsHandled() throws Exception {
		File chartDir = createMockChart();

		new CommandLine(installCommand).execute("my-release", chartDir.getAbsolutePath(), "--dry-run=bogus");

		// invalid mode is reported, not crashed; installAction is never invoked
		verify(installAction, never()).install(any(InstallOptions.class));
	}

	@Test
	void testInstallCommandAtomicUninstallsOnFailure() throws Exception {
		File chartDir = createMockChart();

		when(installAction.install(any(InstallOptions.class))).thenThrow(new RuntimeException("deploy failed"));

		CommandLine cmd = new CommandLine(installCommand);
		cmd.execute("my-release", chartDir.getAbsolutePath(), "--atomic");

		verify(uninstallAction).uninstall(any(UninstallOptions.class));
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
