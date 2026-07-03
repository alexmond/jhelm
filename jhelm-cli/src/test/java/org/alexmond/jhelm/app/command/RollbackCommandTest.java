package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.RollbackOptions;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.KubeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RollbackCommandTest {

	@Mock
	private RollbackAction rollbackAction;

	@Mock
	private KubeService kubeService;

	private RollbackCommand rollbackCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		rollbackCommand = new RollbackCommand(rollbackAction, kubeService, enabledPolicy());
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
	void testRollbackBlockedInReadOnlyMode() throws Exception {
		RollbackCommand readOnlyCommand = new RollbackCommand(rollbackAction, kubeService, readOnlyPolicy());

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
