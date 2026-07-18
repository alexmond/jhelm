package org.alexmond.jhelm.core.service;

import java.util.Locale;

import org.alexmond.jhelm.pluginapi.JhelmPostRenderer;

/** ServiceLoader-registered test post-renderer (uppercases the manifest). */
public class UppercaseTestPostRenderer implements JhelmPostRenderer {

	@Override
	public String name() {
		return "uppercase-test";
	}

	@Override
	public String postRender(String manifest) {
		return manifest.toUpperCase(Locale.ROOT);
	}

}
