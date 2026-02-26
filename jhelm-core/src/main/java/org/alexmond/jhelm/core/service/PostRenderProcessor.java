package org.alexmond.jhelm.core.service;

/**
 * Callback interface for post-render manifest processing. Implementations can transform
 * the rendered YAML manifest before it is applied to the cluster.
 */
@FunctionalInterface
public interface PostRenderProcessor {

	/**
	 * Process a rendered manifest.
	 * @param renderedManifest the rendered YAML manifest
	 * @return the transformed manifest
	 * @throws Exception if processing fails
	 */
	String process(String renderedManifest) throws Exception;

}
