package org.alexmond.jhelm.pluginapi;

import java.util.Locale;

/**
 * Test fixture registered as a {@code ServiceLoader} service (see
 * {@code META-INF/services/org.alexmond.jhelm.pluginapi.JhelmPostRenderer}).
 */
public class ExampleServicePostRenderer implements JhelmPostRenderer {

	@Override
	public String name() {
		return "example-service";
	}

	@Override
	public String postRender(String manifest) {
		return manifest.toUpperCase(Locale.ROOT);
	}

}
