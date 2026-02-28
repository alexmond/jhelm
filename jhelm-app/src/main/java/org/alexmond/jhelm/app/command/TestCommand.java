package org.alexmond.jhelm.app.command;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.TestAction.TestResult;
import org.alexmond.jhelm.core.action.TestAction.TestStatus;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * CLI command that runs test hooks for a Helm release.
 */
@Component
@CommandLine.Command(name = "test", mixinStandardHelpOptions = true,
		description = "Run the test hooks for a named release")
@Slf4j
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public class TestCommand implements Runnable {

	private final TestAction testAction;

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--timeout" }, defaultValue = "300",
			description = "time in seconds to wait for test to complete (default: 300)")
	private int timeout;

	@CommandLine.Option(names = { "--logs" }, description = "dump the logs from test hooks")
	private boolean showLogs;

	public TestCommand(TestAction testAction) {
		this.testAction = testAction;
	}

	@Override
	public void run() {
		try {
			List<TestResult> results = testAction.test(name, namespace, timeout);

			if (results.isEmpty()) {
				CliOutput.println(CliOutput.warn("No test hooks found for release '" + name + "'"));
				return;
			}

			boolean allPassed = true;
			for (TestResult result : results) {
				printResult(result);
				if (result.getStatus() != TestStatus.PASSED) {
					allPassed = false;
				}
			}

			if (allPassed) {
				CliOutput.println(CliOutput.success("All tests passed for release '" + name + "'"));
			}
			else {
				CliOutput.errPrintln(CliOutput.error("Some tests failed for release '" + name + "'"));
			}
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error running tests: " + ex.getMessage()));
		}
	}

	private void printResult(TestResult result) {
		String statusStr = switch (result.getStatus()) {
			case PASSED -> CliOutput.success("PASSED");
			case FAILED -> CliOutput.error("FAILED");
			case RUNNING -> CliOutput.warn("RUNNING");
		};
		CliOutput.println(CliOutput.bold("TEST:") + " " + result.getKind() + "/" + result.getName() + " " + statusStr);

		if (result.getMessage() != null) {
			CliOutput.println("  " + result.getMessage());
		}

		if (result.getPendingResources() != null) {
			for (ResourceStatus rs : result.getPendingResources()) {
				CliOutput.println("  " + CliOutput.error("\u2717") + " " + rs.getKind() + "/" + rs.getName() + ": "
						+ rs.getMessage());
			}
		}

		if (showLogs) {
			printLogs(result);
		}
	}

	private void printLogs(TestResult result) {
		if (result.getHook() == null) {
			return;
		}
		try {
			String logs = testAction.getLogs(namespace, result.getHook());
			if (!logs.isEmpty()) {
				CliOutput.println(CliOutput.bold("  LOGS:"));
				CliOutput.println("  " + logs.replace("\n", "\n  "));
			}
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("Failed to fetch logs for {}/{}: {}", result.getKind(), result.getName(), ex.getMessage());
			}
		}
	}

}
