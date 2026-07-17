package org.alexmond.jhelm.app.plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelmPluginDiscoveryTest {

	@TempDir
	Path pluginsDir;

	private HelmPluginDiscovery discovery() {
		HelmPluginPaths paths = new HelmPluginPaths(Map.of("HELM_PLUGINS", this.pluginsDir.toString())::get,
				Path.of("/nohome"));
		return new HelmPluginDiscovery(paths);
	}

	private void writePlugin(String dir, String yaml) throws Exception {
		Path pluginDir = Files.createDirectories(this.pluginsDir.resolve(dir));
		Files.writeString(pluginDir.resolve("plugin.yaml"), yaml);
	}

	@Test
	void discoversPluginsSortedByName() throws Exception {
		writePlugin("s3", "name: s3\nversion: 0.16.0\ncommand: run-s3\n");
		writePlugin("diff", "name: diff\nversion: 3.9.0\ncommand: run-diff\n");
		List<DiscoveredHelmPlugin> found = discovery().discover();
		assertEquals(2, found.size());
		assertEquals("diff", found.get(0).name());
		assertEquals("s3", found.get(1).name());
		assertTrue(found.get(0).directory().endsWith("diff"));
	}

	@Test
	void skipsDirectoriesWithoutManifest() throws Exception {
		writePlugin("diff", "name: diff\ncommand: run\n");
		Files.createDirectories(this.pluginsDir.resolve("not-a-plugin"));
		List<DiscoveredHelmPlugin> found = discovery().discover();
		assertEquals(1, found.size());
		assertEquals("diff", found.get(0).name());
	}

	@Test
	void fallsBackToDirectoryNameWhenManifestNameMissing() throws Exception {
		writePlugin("myplugin", "version: 1.0.0\ncommand: run\n");
		DiscoveredHelmPlugin plugin = discovery().find("myplugin").orElseThrow();
		assertEquals("myplugin", plugin.name());
	}

	@Test
	void returnsEmptyWhenPluginsDirAbsent() {
		HelmPluginPaths paths = new HelmPluginPaths(Map.of("HELM_PLUGINS", "/does/not/exist")::get, Path.of("/nohome"));
		assertTrue(new HelmPluginDiscovery(paths).discover().isEmpty());
	}

	@Test
	void findReturnsRequestedPlugin() throws Exception {
		writePlugin("diff", "name: diff\ncommand: run\n");
		writePlugin("s3", "name: s3\ncommand: run\n");
		assertEquals("s3", discovery().find("s3").orElseThrow().name());
		assertTrue(discovery().find("absent").isEmpty());
	}

}
