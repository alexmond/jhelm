package org.alexmond.jhelm.app.command;

import java.time.Instant;
import java.util.concurrent.Callable;

import org.alexmond.jhelm.app.VersionInfo;
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

	private final VersionInfo versionInfo;

	/**
	 * Creates the command.
	 * @param versionInfo the shared build-info-backed version source
	 */
	public VersionCommand(VersionInfo versionInfo) {
		this.versionInfo = versionInfo;
	}

	/**
	 * Resolves the jhelm version from Spring Boot build-info (with a jar-manifest
	 * fallback) via {@link VersionInfo}, so it agrees with {@code jhelm --version}.
	 * @return the version string, prefixed with {@code v}
	 */
	String versionString() {
		return "v" + this.versionInfo.version();
	}

	@Override
	public Integer call() {
		if (this.shortOutput) {
			CliOutput.println(versionString());
		}
		else {
			StringBuilder details = new StringBuilder("Java ").append(System.getProperty("java.version"));
			Instant built = this.versionInfo.buildTime();
			if (built != null) {
				details.append(", built ").append(built);
			}
			CliOutput.println("jhelm version " + versionString() + " (" + details + ")");
		}
		return CommandLine.ExitCode.OK;
	}

}
