package org.alexmond.jhelm.app.command;

import java.util.List;

import org.alexmond.jhelm.core.action.LintAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
		when(lintAction.lint(anyString(), anyMap(), anyBoolean(), anyBoolean(), any()))
			.thenReturn(new LintAction.LintResult("./", List.of(), List.of()));
		CommandLine cmd = new CommandLine(lintCommand);
		int exitCode = cmd.execute(".");
		assertEquals(0, exitCode);
	}

	@Test
	void testLintCommandWithWarnings() {
		when(lintAction.lint(anyString(), anyMap(), anyBoolean(), anyBoolean(), any()))
			.thenReturn(new LintAction.LintResult("./", List.of(), List.of("missing description")));
		CommandLine cmd = new CommandLine(lintCommand);
		int exitCode = cmd.execute(".");
		assertEquals(0, exitCode);
	}

	@Test
	void testLintThreadsSubchartsQuietAndKubeVersion() {
		when(lintAction.lint(anyString(), anyMap(), anyBoolean(), anyBoolean(), any()))
			.thenReturn(new LintAction.LintResult("./chart", List.of(), List.of()));
		CommandLine cmd = new CommandLine(lintCommand);
		int exitCode = cmd.execute(".", "--with-subcharts", "--kube-version", "1.30.0", "--quiet");
		assertEquals(0, exitCode);
		verify(lintAction).lint(eq("."), anyMap(), eq(false), eq(true), eq("1.30.0"));
	}

	@Test
	void testLintCommandWithErrors() {
		when(lintAction.lint(anyString(), anyMap(), anyBoolean(), anyBoolean(), any()))
			.thenReturn(new LintAction.LintResult("./", List.of("chart name is required"), List.of()));
		CommandLine cmd = new CommandLine(lintCommand);
		int exitCode = cmd.execute(".");
		// #647: a chart that fails lint must exit non-zero, like `helm lint`.
		assertEquals(CommandLine.ExitCode.SOFTWARE, exitCode);
	}

}
