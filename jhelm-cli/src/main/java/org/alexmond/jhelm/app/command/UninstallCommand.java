package org.alexmond.jhelm.app.command;

import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.service.CascadePolicy;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Implements {@code jhelm uninstall RELEASE}, removing a release and its resources from
 * the cluster.
 */
@Component
@CommandLine.Command(name = "uninstall", mixinStandardHelpOptions = true, description = "Uninstall a release")
@Slf4j
public class UninstallCommand implements Callable<Integer> {

	private final UninstallAction uninstallAction;

	private final JhelmSecurityPolicy securityPolicy;

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--no-hooks" }, description = "prevent hooks from running during this operation")
	private boolean noHooks;

	@CommandLine.Option(names = { "--keep-history" },
			description = "remove all associated resources and mark the release as deleted, but retain the release history")
	private boolean keepHistory;

	@CommandLine.Option(names = { "--dry-run" },
			description = "simulate the uninstall without deleting resources or history")
	private boolean dryRun;

	@CommandLine.Option(names = { "--wait" },
			description = "wait until the release's resources are removed from the cluster")
	private boolean wait;

	@CommandLine.Option(names = { "--timeout" }, defaultValue = "300",
			description = "timeout in seconds for --wait (default 300)")
	private int timeout;

	@CommandLine.Option(names = { "--cascade" }, defaultValue = "background",
			description = "resource deletion propagation: background, foreground, or orphan")
	private String cascade;

	@CommandLine.Option(names = { "--description" },
			description = "custom description stored on the release when --keep-history is set")
	private String description;

	/**
	 * Creates the command.
	 * @param uninstallAction the action that uninstalls the release
	 * @param securityPolicy the unified access-mode policy; the operation is refused
	 * unless mutating operations are enabled
	 */
	public UninstallCommand(UninstallAction uninstallAction, JhelmSecurityPolicy securityPolicy) {
		this.uninstallAction = uninstallAction;
		this.securityPolicy = securityPolicy;
	}

	@Override
	public Integer call() {
		if (MutatingGuard.blocked(securityPolicy)) {
			return CommandLine.ExitCode.SOFTWARE;
		}
		try {
			uninstallAction.uninstall(UninstallOptions.builder()
				.releaseName(name)
				.namespace(namespace)
				.noHooks(noHooks)
				.keepHistory(keepHistory)
				.dryRun(dryRun)
				.wait(wait)
				.timeout(timeout)
				.cascade(CascadePolicy.fromString(cascade))
				.description(description)
				.build());
			String verb = dryRun ? "\" would be uninstalled (dry run)" : "\" uninstalled";
			CliOutput.println(CliOutput.success("release \"" + name + verb));
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error uninstalling release: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

}
