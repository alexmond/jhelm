package org.alexmond.jhelm.app.command;

import java.util.List;

import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.TestAction.TestResult;
import org.alexmond.jhelm.core.action.TestAction.TestStatus;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.alexmond.jhelm.core.exception.ReleaseNotFoundException;
import org.alexmond.jhelm.core.model.HelmHook;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestCommandTest {

	@Mock
	private TestAction testAction;

	private TestCommand testCommand;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		testCommand = new TestCommand(testAction, enabledPolicy());
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
	void testPassingTests() throws Exception {
		TestResult result = TestResult.builder().kind("Pod").name("my-test").status(TestStatus.PASSED).build();
		when(testAction.test(anyString(), anyString(), anyInt())).thenReturn(List.of(result));

		CommandLine cmd = new CommandLine(testCommand);
		cmd.execute("my-release", "-n", "default");
	}

	@Test
	void testFailingTests() throws Exception {
		List<ResourceStatus> pending = List
			.of(ResourceStatus.builder().kind("Pod").name("my-test").message("not ready").build());
		TestResult result = TestResult.builder()
			.kind("Pod")
			.name("my-test")
			.status(TestStatus.FAILED)
			.message("Timeout waiting for test to complete")
			.pendingResources(pending)
			.build();
		when(testAction.test(anyString(), anyString(), anyInt())).thenReturn(List.of(result));

		CommandLine cmd = new CommandLine(testCommand);
		cmd.execute("my-release");
	}

	@Test
	void testNoTestHooks() throws Exception {
		when(testAction.test(anyString(), anyString(), anyInt())).thenReturn(List.of());

		CommandLine cmd = new CommandLine(testCommand);
		cmd.execute("my-release");
	}

	@Test
	void testReleaseNotFound() throws Exception {
		when(testAction.test(anyString(), anyString(), anyInt()))
			.thenThrow(ReleaseNotFoundException.forRelease("missing", "default"));

		CommandLine cmd = new CommandLine(testCommand);
		cmd.execute("missing");
	}

	@Test
	void testWithLogsFlag() throws Exception {
		HelmHook hook = HelmHook.builder().kind("Pod").name("my-test").yaml("apiVersion: v1").build();
		TestResult result = TestResult.builder()
			.kind("Pod")
			.name("my-test")
			.status(TestStatus.PASSED)
			.hook(hook)
			.build();
		when(testAction.test(anyString(), anyString(), anyInt())).thenReturn(List.of(result));
		when(testAction.getLogs(eq("default"), eq(hook))).thenReturn("Pod/my-test: Completed\n");

		CommandLine cmd = new CommandLine(testCommand);
		cmd.execute("my-release", "--logs");
	}

	@Test
	void testWithCustomTimeout() throws Exception {
		TestResult result = TestResult.builder().kind("Pod").name("my-test").status(TestStatus.PASSED).build();
		when(testAction.test(anyString(), anyString(), anyInt())).thenReturn(List.of(result));

		CommandLine cmd = new CommandLine(testCommand);
		cmd.execute("my-release", "--timeout", "60");
	}

	@Test
	void testTestBlockedInReadOnlyMode() throws Exception {
		// #653: READ_ONLY (the default) must refuse the cluster-mutating test and not run
		// it.
		TestCommand readOnly = new TestCommand(testAction, readOnlyPolicy());

		int exitCode = new CommandLine(readOnly).execute("my-release", "-n", "default");

		assertNotEquals(CommandLine.ExitCode.OK, exitCode, "test must be refused in READ_ONLY");
		verify(testAction, never()).test(anyString(), anyString(), anyInt());
	}

}
