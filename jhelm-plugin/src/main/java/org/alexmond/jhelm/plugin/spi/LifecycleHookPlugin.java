package org.alexmond.jhelm.plugin.spi;

import org.alexmond.jhelm.plugin.exception.PluginExecutionException;
import org.alexmond.jhelm.plugin.model.PluginEvent;

/**
 * Plugin that executes custom logic at lifecycle points (pre-install, post-install,
 * etc.).
 */
public interface LifecycleHookPlugin extends Plugin {

	/**
	 * Handle a lifecycle event.
	 * @param event the lifecycle event
	 * @throws PluginExecutionException if the plugin fails
	 */
	void onEvent(PluginEvent event) throws PluginExecutionException;

}
