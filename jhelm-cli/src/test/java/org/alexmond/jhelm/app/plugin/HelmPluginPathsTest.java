package org.alexmond.jhelm.app.plugin;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelmPluginPathsTest {

	private static final Path HOME = Path.of("/home/tester");

	private HelmPluginPaths paths(Map<String, String> env) {
		return new HelmPluginPaths(env::get, HOME);
	}

	@Test
	void defaultsToXdgLinuxLayoutUnderHome() {
		HelmPluginPaths paths = paths(Map.of());
		assertEquals(Path.of("/home/tester/.local/share/helm"), paths.dataHome());
		assertEquals(Path.of("/home/tester/.config/helm"), paths.configHome());
		assertEquals(Path.of("/home/tester/.cache/helm"), paths.cacheHome());
		assertEquals(Path.of("/home/tester/.local/share/helm/plugins"), paths.pluginsDir());
	}

	@Test
	void honorsXdgDataHome() {
		HelmPluginPaths paths = paths(Map.of("XDG_DATA_HOME", "/xdg/data"));
		assertEquals(Path.of("/xdg/data/helm"), paths.dataHome());
		assertEquals(Path.of("/xdg/data/helm/plugins"), paths.pluginsDir());
	}

	@Test
	void helmDataHomeWinsOverXdg() {
		HelmPluginPaths paths = paths(Map.of("XDG_DATA_HOME", "/xdg/data", "HELM_DATA_HOME", "/opt/helm"));
		assertEquals(Path.of("/opt/helm"), paths.dataHome());
		assertEquals(Path.of("/opt/helm/plugins"), paths.pluginsDir());
	}

	@Test
	void helmPluginsOverridesDirectly() {
		HelmPluginPaths paths = paths(Map.of("HELM_DATA_HOME", "/opt/helm", "HELM_PLUGINS", "/custom/plugins"));
		assertEquals(Path.of("/custom/plugins"), paths.pluginsDir());
	}

	@Test
	void blankValuesAreIgnored() {
		HelmPluginPaths paths = paths(Map.of("HELM_DATA_HOME", "   "));
		assertEquals(Path.of("/home/tester/.local/share/helm"), paths.dataHome());
	}

}
