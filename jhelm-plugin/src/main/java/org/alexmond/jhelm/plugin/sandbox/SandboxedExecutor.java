package org.alexmond.jhelm.plugin.sandbox;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.plugin.exception.PluginExecutionException;
import org.alexmond.jhelm.plugin.exception.PluginTimeoutException;

/**
 * Enforces timeout limits on plugin execution using Java 21 virtual threads.
 */
@Slf4j
public class SandboxedExecutor {

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	/**
	 * Execute a task within the sandbox constraints.
	 * @param <T> the result type
	 * @param pluginName the plugin name (for error messages)
	 * @param config the sandbox configuration
	 * @param task the task to execute
	 * @return the task result
	 * @throws PluginExecutionException if execution fails
	 * @throws PluginTimeoutException if execution exceeds the timeout
	 */
	public <T> T execute(String pluginName, SandboxConfig config, Callable<T> task)
			throws PluginExecutionException, PluginTimeoutException {
		try {
			Future<T> future = executor.submit(task);
			return future.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
		}
		catch (TimeoutException ex) {
			throw new PluginTimeoutException(
					"Plugin '" + pluginName + "' exceeded timeout of " + config.getTimeoutSeconds() + "s", ex);
		}
		catch (ExecutionException ex) {
			throw new PluginExecutionException("Plugin '" + pluginName + "' execution failed", ex.getCause());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new PluginExecutionException("Plugin '" + pluginName + "' execution interrupted", ex);
		}
	}

}
