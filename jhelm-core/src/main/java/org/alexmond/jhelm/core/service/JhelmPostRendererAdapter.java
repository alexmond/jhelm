package org.alexmond.jhelm.core.service;

import java.io.IOException;

import org.alexmond.jhelm.pluginapi.JhelmPluginException;
import org.alexmond.jhelm.pluginapi.JhelmPostRenderer;

/**
 * Adapts a Java {@link JhelmPostRenderer} plugin to the internal
 * {@link PostRenderProcessor} used by the render pipeline, translating a
 * {@link JhelmPluginException} into the {@link IOException} the pipeline expects.
 */
public class JhelmPostRendererAdapter implements PostRenderProcessor {

	private final JhelmPostRenderer plugin;

	/**
	 * Wraps a post-renderer plugin.
	 * @param plugin the Java post-renderer plugin
	 */
	public JhelmPostRendererAdapter(JhelmPostRenderer plugin) {
		this.plugin = plugin;
	}

	@Override
	public String process(String renderedManifest) throws IOException {
		try {
			return this.plugin.postRender(renderedManifest);
		}
		catch (JhelmPluginException ex) {
			throw new IOException("post-renderer plugin '" + this.plugin.name() + "' failed: " + ex.getMessage(), ex);
		}
	}

}
