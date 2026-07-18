package org.alexmond.jhelm.pluginapi.sample;

import org.alexmond.jhelm.pluginapi.JhelmPostRenderer;

/**
 * Sample {@link JhelmPostRenderer} that prepends a banner comment to the rendered
 * manifest. A real post-renderer might run kustomize, inject sidecars, or redact values.
 */
public class BannerPostRenderer implements JhelmPostRenderer {

	@Override
	public String name() {
		return "banner";
	}

	@Override
	public String postRender(String manifest) {
		return "# rendered via the jhelm sample post-renderer\n" + manifest;
	}

}
