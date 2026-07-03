package org.alexmond.jhelm.app.command;

import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
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

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--no-hooks" }, description = "prevent hooks from running during this operation")
	private boolean noHooks;

	@CommandLine.Option(names = { "--keep-history" },
			description = "remove all associated resources and mark the release as deleted, but retain the release history")
	private boolean keepHistory;

	/**
	 * Creates the command.
	 * @param uninstallAction the action that uninstalls the release
	 */
	public UninstallCommand(UninstallAction uninstallAction) {
		this.uninstallAction = uninstallAction;
	}

	@Override
	public Integer call() {
		try {
			uninstallAction.uninstall(UninstallOptions.builder()
				.releaseName(name)
				.namespace(namespace)
				.noHooks(noHooks)
				.keepHistory(keepHistory)
				.build());
			CliOutput.println(CliOutput.success("release \"" + name + "\" uninstalled"));
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error uninstalling release: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

}
