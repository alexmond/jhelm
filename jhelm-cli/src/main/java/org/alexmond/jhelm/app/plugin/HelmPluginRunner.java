package org.alexmond.jhelm.app.plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Runs a resolved Helm-plugin command as a child process. Abstracted behind an interface
 * so the dispatcher can be unit-tested against a fake runner while production uses a real
 * {@link ProcessHelmPluginRunner}.
 */
public interface HelmPluginRunner {

	/**
	 * Executes a plugin command, wiring the process's stdio to the current terminal and
	 * exporting the given environment.
	 * @param command the full argument vector (plugin command plus its arguments)
	 * @param env the {@code HELM_*} environment to add to the child process
	 * @param workingDir the working directory for the child process (the plugin dir)
	 * @return the child process exit code
	 * @throws IOException if the process cannot be started
	 * @throws InterruptedException if the current thread is interrupted while waiting
	 */
	int run(List<String> command, Map<String, String> env, Path workingDir) throws IOException, InterruptedException;

}
