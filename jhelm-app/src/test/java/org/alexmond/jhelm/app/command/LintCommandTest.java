package org.alexmond.jhelm.app.command;

import java.util.List;

import org.alexmond.jhelm.core.action.LintAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class LintCommandTest {

	@Mock
	private LintAction lintAction;

	private LintCommand lintCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		lintCommand = new LintCommand(lintAction);
	}

	@Test
	void testLintCommandSuccess() {
		when(lintAction.lint(anyString(), anyMap(), anyBoolean()))
			.thenReturn(new LintAction.LintResult("./", List.of(), List.of()));
		CommandLine cmd = new CommandLine(lintCommand);
		int exitCode = cmd.execute(".");
		assertEquals(0, exitCode);
	}

	@Test
	void testLintCommandWithWarnings() {
		when(lintAction.lint(anyString(), anyMap(), anyBoolean()))
			.thenReturn(new LintAction.LintResult("./", List.of(), List.of("missing description")));
		CommandLine cmd = new CommandLine(lintCommand);
		int exitCode = cmd.execute(".");
		assertEquals(0, exitCode);
	}

	@Test
	void testLintCommandWithErrors() {
		when(lintAction.lint(anyString(), anyMap(), anyBoolean()))
			.thenReturn(new LintAction.LintResult("./", List.of("chart name is required"), List.of()));
		CommandLine cmd = new CommandLine(lintCommand);
		int exitCode = cmd.execute(".");
		assertEquals(0, exitCode);
	}

}
