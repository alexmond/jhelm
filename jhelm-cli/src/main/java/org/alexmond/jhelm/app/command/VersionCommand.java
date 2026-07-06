package org.alexmond.jhelm.app.command;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.alexmond.gotmpl4j.GoTemplate;
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

	@CommandLine.Option(names = { "--template" },
			description = "template for the output (Go template against .Version, .GitCommit, .GitTreeState, .GoVersion)")
	private String template;

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
		if (this.template != null) {
			return renderTemplate();
		}
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

	// Renders --template as a Go template against the version info, matching
	// `helm version --template`. jhelm has no git metadata, so GitCommit/GitTreeState are
	// empty and GoVersion carries the running Java version.
	private Integer renderTemplate() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("Version", versionString());
		data.put("GitCommit", "");
		data.put("GitTreeState", "");
		data.put("GoVersion", System.getProperty("java.version"));
		try {
			GoTemplate engine = new GoTemplate();
			engine.parse("version", this.template);
			CliOutput.printf("%s", engine.render("version", data));
			return CommandLine.ExitCode.OK;
		}
		catch (RuntimeException ex) {
			CliOutput.errPrintln(CliOutput.error("Error rendering version template: " + ex.getMessage()));
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

}
