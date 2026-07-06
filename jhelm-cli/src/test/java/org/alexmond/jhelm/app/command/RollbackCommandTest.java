package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.RollbackOptions;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.alexmond.jhelm.core.model.Release;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RollbackCommandTest {

	@Mock
	private RollbackAction rollbackAction;

	private RollbackCommand rollbackCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		rollbackCommand = new RollbackCommand(rollbackAction, enabledPolicy());
	}

	@Test
	void testRollbackCommandSuccess() throws Exception {
		Release release = Release.builder().name("my-release").namespace("default").manifest("---\n").build();
		when(rollbackAction.rollback(any(RollbackOptions.class))).thenReturn(release);

		CommandLine cmd = new CommandLine(rollbackCommand);
		cmd.execute("my-release", "1", "-n", "default");
	}

	@Test
	void testRollbackCommandDefaultNamespace() throws Exception {
		Release release = Release.builder().name("my-release").namespace("default").manifest("---\n").build();
		when(rollbackAction.rollback(any(RollbackOptions.class))).thenReturn(release);

		CommandLine cmd = new CommandLine(rollbackCommand);
		cmd.execute("my-release", "2");
	}

	@Test
	void testRollbackCommandWithError() throws Exception {
		doThrow(new RuntimeException("Test error")).when(rollbackAction).rollback(any(RollbackOptions.class));

		CommandLine cmd = new CommandLine(rollbackCommand);
		cmd.execute("my-release", "1");
	}

	@Test
	void testRollbackFlagsReachOptions() throws Exception {
		Release release = Release.builder().name("my-release").namespace("default").manifest("---\n").build();
		ArgumentCaptor<RollbackOptions> captor = ArgumentCaptor.forClass(RollbackOptions.class);
		when(rollbackAction.rollback(captor.capture())).thenReturn(release);

		int exit = new CommandLine(rollbackCommand).execute("my-release", "1", "--force", "--cleanup-on-fail",
				"--recreate-pods", "--wait", "--wait-for-jobs", "--timeout", "120");

		assertEquals(CommandLine.ExitCode.OK, exit);
		RollbackOptions opts = captor.getValue();
		assertTrue(opts.isForce());
		assertTrue(opts.isCleanupOnFail());
		assertTrue(opts.isRecreatePods());
		assertTrue(opts.isWait());
		assertTrue(opts.isWaitForJobs());
		assertEquals(120, opts.getTimeout());
	}

	@Test
	void testRollbackDryRunPassesFlag() throws Exception {
		Release release = Release.builder().name("my-release").namespace("default").manifest("---\n").build();
		ArgumentCaptor<RollbackOptions> captor = ArgumentCaptor.forClass(RollbackOptions.class);
		when(rollbackAction.rollback(captor.capture())).thenReturn(release);

		int exit = new CommandLine(rollbackCommand).execute("my-release", "1", "--dry-run");

		assertEquals(CommandLine.ExitCode.OK, exit);
		assertTrue(captor.getValue().isDryRun());
	}

	@Test
	void testRollbackBlockedInReadOnlyMode() throws Exception {
		RollbackCommand readOnlyCommand = new RollbackCommand(rollbackAction, readOnlyPolicy());

		CommandLine cmd = new CommandLine(readOnlyCommand);
		int exitCode = cmd.execute("my-release", "1", "-n", "default");

		assertNotEquals(CommandLine.ExitCode.OK, exitCode, "rollback must be refused in READ_ONLY");
		verify(rollbackAction, never()).rollback(any(RollbackOptions.class));
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

}
