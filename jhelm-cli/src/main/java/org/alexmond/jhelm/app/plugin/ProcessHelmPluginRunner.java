package org.alexmond.jhelm.app.plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Runs a Helm plugin as a child process with inherited stdio, so an interactive plugin
 * (helm-diff, helm-secrets, …) reads and writes the user's terminal directly and its exit
 * code becomes jhelm's exit code — matching how {@code helm} invokes a plugin.
 */
@Component
public class ProcessHelmPluginRunner implements HelmPluginRunner {

	@Override
	public int run(List<String> command, Map<String, String> env, Path workingDir)
			throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(command).inheritIO();
		if (workingDir != null) {
			builder.directory(workingDir.toFile());
		}
		builder.environment().putAll(env);
		Process process = builder.start();
		return process.waitFor();
	}

}
