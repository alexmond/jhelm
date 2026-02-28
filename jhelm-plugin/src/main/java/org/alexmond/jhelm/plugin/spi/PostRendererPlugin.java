package org.alexmond.jhelm.plugin.spi;

import org.alexmond.jhelm.plugin.exception.PluginExecutionException;

/**
 * Plugin that transforms rendered YAML manifests before they are applied to the cluster.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface") // extends Plugin which has multiple
														// abstract methods
public interface PostRendererPlugin extends Plugin {

	/**
	 * Transform a rendered manifest.
	 * @param renderedManifest the full rendered YAML
	 * @return the transformed YAML
	 * @throws PluginExecutionException if the plugin fails
	 */
	String postRender(String renderedManifest) throws PluginExecutionException;

}
