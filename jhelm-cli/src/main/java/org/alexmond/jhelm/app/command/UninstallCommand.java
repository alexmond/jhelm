package org.alexmond.jhelm.app.command;

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
public class UninstallCommand implements Runnable {

	private final UninstallAction uninstallAction;

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--no-hooks" }, description = "prevent hooks from running during this operation")
	private boolean noHooks;

	/**
	 * Creates the command.
	 * @param uninstallAction the action that uninstalls the release
	 */
	public UninstallCommand(UninstallAction uninstallAction) {
		this.uninstallAction = uninstallAction;
	}

	@Override
	public void run() {
		try {
			uninstallAction
				.uninstall(UninstallOptions.builder().releaseName(name).namespace(namespace).noHooks(noHooks).build());
			CliOutput.println(CliOutput.success("release \"" + name + "\" uninstalled"));
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error uninstalling release: " + ex.getMessage()));
		}
	}

}
