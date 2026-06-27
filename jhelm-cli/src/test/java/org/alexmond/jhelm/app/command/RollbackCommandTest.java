package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.RollbackOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

class RollbackCommandTest {

	@Mock
	private RollbackAction rollbackAction;

	private RollbackCommand rollbackCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		rollbackCommand = new RollbackCommand(rollbackAction);
	}

	@Test
	void testRollbackCommandSuccess() throws Exception {
		doNothing().when(rollbackAction).rollback(any(RollbackOptions.class));

		CommandLine cmd = new CommandLine(rollbackCommand);
		cmd.execute("my-release", "1", "-n", "default");
	}

	@Test
	void testRollbackCommandDefaultNamespace() throws Exception {
		doNothing().when(rollbackAction).rollback(any(RollbackOptions.class));

		CommandLine cmd = new CommandLine(rollbackCommand);
		cmd.execute("my-release", "2");
	}

	@Test
	void testRollbackCommandWithError() throws Exception {
		doThrow(new RuntimeException("Test error")).when(rollbackAction).rollback(any(RollbackOptions.class));

		CommandLine cmd = new CommandLine(rollbackCommand);
		cmd.execute("my-release", "1");
	}

}
