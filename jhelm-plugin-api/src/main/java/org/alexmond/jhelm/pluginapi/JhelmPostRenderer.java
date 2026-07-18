package org.alexmond.jhelm.pluginapi;

/**
 * A plugin that post-processes a rendered manifest before it is applied or emitted — the
 * in-process, Java equivalent of a Helm post-renderer. Applied by {@code template},
 * {@code install}, and {@code upgrade} after rendering.
 */
public interface JhelmPostRenderer extends JhelmPlugin {

	/**
	 * Transforms a fully rendered manifest.
	 * @param manifest the rendered multi-document YAML manifest
	 * @return the transformed manifest
	 * @throws JhelmPluginException if the transformation fails (aborts the operation)
	 */
	String postRender(String manifest) throws JhelmPluginException;

}
