package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.alexmond.jhelm.core.service.CascadePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UninstallCommandTest {

	@Mock
	private UninstallAction uninstallAction;

	private UninstallCommand uninstallCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		uninstallCommand = new UninstallCommand(uninstallAction, enabledPolicy());
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
	void testUninstallCommandSuccess() throws Exception {
		doNothing().when(uninstallAction).uninstall(any(UninstallOptions.class));

		CommandLine cmd = new CommandLine(uninstallCommand);
		cmd.execute("my-release", "-n", "default");
	}

	@Test
	void testUninstallCommandDefaultNamespace() throws Exception {
		doNothing().when(uninstallAction).uninstall(any(UninstallOptions.class));

		CommandLine cmd = new CommandLine(uninstallCommand);
		cmd.execute("my-release");
	}

	@Test
	void testUninstallCommandWithError() throws Exception {
		doThrow(new RuntimeException("Test error")).when(uninstallAction).uninstall(any(UninstallOptions.class));

		CommandLine cmd = new CommandLine(uninstallCommand);
		cmd.execute("my-release");
	}

	@Test
	void testUninstallFlagsReachOptions() throws Exception {
		ArgumentCaptor<UninstallOptions> captor = ArgumentCaptor.forClass(UninstallOptions.class);
		doNothing().when(uninstallAction).uninstall(captor.capture());

		int exit = new CommandLine(uninstallCommand).execute("my-release", "--wait", "--timeout", "90", "--cascade",
				"foreground", "--keep-history", "--description", "cleanup");

		assertEquals(CommandLine.ExitCode.OK, exit);
		UninstallOptions opts = captor.getValue();
		assertTrue(opts.isWait());
		assertEquals(90, opts.getTimeout());
		assertEquals(CascadePolicy.FOREGROUND, opts.getCascade());
		assertTrue(opts.isKeepHistory());
		assertEquals("cleanup", opts.getDescription());
	}

	@Test
	void testUninstallDryRunReportsWithoutError() throws Exception {
		ArgumentCaptor<UninstallOptions> captor = ArgumentCaptor.forClass(UninstallOptions.class);
		doNothing().when(uninstallAction).uninstall(captor.capture());

		int exit = new CommandLine(uninstallCommand).execute("my-release", "--dry-run");

		assertEquals(CommandLine.ExitCode.OK, exit);
		assertTrue(captor.getValue().isDryRun());
	}

	@Test
	void testUninstallBlockedInReadOnlyMode() throws Exception {
		// #653: READ_ONLY (the default) must refuse a cluster-mutating uninstall and not
		// run it.
		UninstallCommand readOnly = new UninstallCommand(uninstallAction, readOnlyPolicy());

		int exitCode = new CommandLine(readOnly).execute("my-release", "-n", "default");

		assertNotEquals(CommandLine.ExitCode.OK, exitCode, "uninstall must be refused in READ_ONLY");
		verify(uninstallAction, never()).uninstall(any(UninstallOptions.class));
	}

}
