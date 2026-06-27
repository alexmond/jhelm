package org.alexmond.jhelm.app.command;

import org.alexmond.jhelm.app.output.CliOutput;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Implements {@code jhelm env}, printing the environment jhelm runs in — the active
 * namespace, the jhelm/Java/OS versions, and which recognized environment variables are
 * set. Mirrors {@code helm env} as a quick way to inspect the client environment.
 */
@Component
@CommandLine.Command(name = "env", mixinStandardHelpOptions = true,
		description = "Print the jhelm client environment information")
public class EnvCommand implements Runnable {

	/** Creates the command. */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public EnvCommand() {
	}

	private static String orDefault(String envVar, String fallback) {
		String value = System.getenv(envVar);
		return ((value != null) && !value.isBlank()) ? value : fallback;
	}

	@Override
	public void run() {
		CliOutput.println("HELM_NAMESPACE=\"" + orDefault("HELM_NAMESPACE", "default") + "\"");
		CliOutput.println("HELM_PASSPHRASE_SET=\"" + (System.getenv("HELM_PASSPHRASE") != null) + "\"");
		CliOutput.println("JHELM_VERSION=\"" + VersionCommand.versionString() + "\"");
		CliOutput.println("JAVA_VERSION=\"" + System.getProperty("java.version") + "\"");
		CliOutput.println("OS=\"" + System.getProperty("os.name") + "/" + System.getProperty("os.arch") + "\"");
	}

}
