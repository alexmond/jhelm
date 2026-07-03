package org.alexmond.jhelm.app.command;

import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.RollbackOptions;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.KubeService;
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

	private final KubeService kubeService;

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

	@CommandLine.Option(names = { "--timeout" }, defaultValue = "300",
			description = "timeout in seconds for --wait (default 300)")
	private int timeout;

	/**
	 * Creates the command.
	 * @param rollbackAction the action that performs the rollback
	 * @param kubeService the Kubernetes service used to wait for resource readiness
	 */
	public RollbackCommand(RollbackAction rollbackAction, KubeService kubeService) {
		this.rollbackAction = rollbackAction;
		this.kubeService = kubeService;
	}

	@Override
	public Integer call() {
		try {
			Release release = rollbackAction.rollback(RollbackOptions.builder()
				.releaseName(name)
				.namespace(namespace)
				.revision(revision)
				.noHooks(noHooks)
				.maxHistory(historyMax)
				.build());
			CliOutput.println(CliOutput.success("Rollback was a success! Happy Helming!"));
			if (wait) {
				kubeService.waitForReady(namespace, release.getManifest(), timeout);
			}
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error during rollback: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

}
