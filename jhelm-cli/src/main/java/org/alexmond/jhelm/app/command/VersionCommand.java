package org.alexmond.jhelm.app.command;

import java.util.concurrent.Callable;

import org.alexmond.jhelm.app.output.CliOutput;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Implements {@code jhelm version}, printing the jhelm version (and the running Java
 * version). Scripts and CI commonly probe {@code helm version} to confirm the binary is
 * present; {@code --short} prints just the version string.
 */
@Component
@CommandLine.Command(name = "version", mixinStandardHelpOptions = true,
		description = "Print the jhelm version information")
public class VersionCommand implements Callable<Integer> {

	@CommandLine.Option(names = { "--short" }, description = "print the version number only")
	private boolean shortOutput;

	/** Creates the command. */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public VersionCommand() {
	}

	/**
	 * Resolves the jhelm version from the jar manifest's {@code Implementation-Version},
	 * falling back to a dev placeholder when running from an exploded build.
	 * @return the version string, prefixed with {@code v}
	 */
	static String versionString() {
		String version = VersionCommand.class.getPackage().getImplementationVersion();
		return ((version != null) && !version.isBlank()) ? "v" + version : "v0.0.0-dev";
	}

	@Override
	public Integer call() {
		if (this.shortOutput) {
			CliOutput.println(versionString());
		}
		else {
			CliOutput
				.println("jhelm version " + versionString() + " (Java " + System.getProperty("java.version") + ")");
		}
		return CommandLine.ExitCode.OK;
	}

}
