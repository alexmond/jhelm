package org.alexmond.jhelm.app.command;

import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.RollbackOptions;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Implements {@code jhelm rollback RELEASE REVISION}, rolling a release back to a
 * previous revision.
 */
@Component
@CommandLine.Command(name = "rollback", mixinStandardHelpOptions = true,
		description = "Roll back a release to a previous revision")
@Slf4j
public class RollbackCommand implements Callable<Integer> {

	private final RollbackAction rollbackAction;

	private final JhelmSecurityPolicy securityPolicy;

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Parameters(index = "1", description = "revision number")
	private int revision;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--no-hooks" }, description = "prevent hooks from running during this operation")
	private boolean noHooks;

	@CommandLine.Option(names = { "--history-max" }, defaultValue = "10",
			description = "limit the maximum number of revisions saved per release (0 = no limit)")
	private int historyMax;

	@CommandLine.Option(names = { "--wait" }, description = "wait until all resources are ready")
	private boolean wait;

	@CommandLine.Option(names = { "--wait-for-jobs" },
			description = "with --wait, also wait for Jobs to complete before marking the rollback done")
	private boolean waitForJobs;

	@CommandLine.Option(names = { "--timeout" }, defaultValue = "300",
			description = "timeout in seconds for --wait (default 300)")
	private int timeout;

	@CommandLine.Option(names = { "--dry-run" },
			description = "simulate the rollback without applying manifests or storing a revision")
	private boolean dryRun;

	@CommandLine.Option(names = { "--force" },
			description = "delete and recreate the target revision's resources instead of patching in place")
	private boolean force;

	@CommandLine.Option(names = { "--cleanup-on-fail" },
			description = "delete resources created during the rollback if it fails")
	private boolean cleanupOnFail;

	@CommandLine.Option(names = { "--recreate-pods" },
			description = "perform a rolling restart of the release's workloads after the rollback (deprecated in Helm)")
	private boolean recreatePods;

	/**
	 * Creates the command.
	 * @param rollbackAction the action that performs the rollback
	 * @param securityPolicy the unified access-mode policy; the operation is refused
	 * unless mutating operations are enabled
	 */
	public RollbackCommand(RollbackAction rollbackAction, JhelmSecurityPolicy securityPolicy) {
		this.rollbackAction = rollbackAction;
		this.securityPolicy = securityPolicy;
	}

	@Override
	public Integer call() {
		if (MutatingGuard.blocked(securityPolicy)) {
			return CommandLine.ExitCode.SOFTWARE;
		}
		try {
			rollbackAction.rollback(RollbackOptions.builder()
				.releaseName(name)
				.namespace(namespace)
				.revision(revision)
				.noHooks(noHooks)
				.maxHistory(historyMax)
				.dryRun(dryRun)
				.force(force)
				.cleanupOnFail(cleanupOnFail)
				.recreatePods(recreatePods)
				.wait(wait)
				.waitForJobs(waitForJobs)
				.timeout(timeout)
				.build());
			CliOutput.println(CliOutput
				.success(dryRun ? "Rollback dry run complete. No changes were applied." : "Rollback was a success!"));
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error during rollback: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

}
