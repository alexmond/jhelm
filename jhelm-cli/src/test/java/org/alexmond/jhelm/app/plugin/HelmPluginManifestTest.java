package org.alexmond.jhelm.app.plugin;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelmPluginManifestTest {

	private final YAMLMapper yaml = YAMLMapper.builder().build();

	private HelmPluginManifest parse(String resource) throws Exception {
		try (InputStream in = getClass().getResourceAsStream(resource)) {
			assertNotNull(in, "missing fixture " + resource);
			return this.yaml.readValue(in, HelmPluginManifest.class);
		}
	}

	@Test
	void parsesSubcommandPluginManifest() throws Exception {
		HelmPluginManifest manifest = parse("/helm-plugins/diff/plugin.yaml");
		assertEquals("diff", manifest.getName());
		assertEquals("3.9.0", manifest.getVersion());
		assertEquals("Preview helm upgrade changes as a diff", manifest.getUsage());
		assertEquals("$HELM_PLUGIN_DIR/bin/diff", manifest.getCommand());
		assertFalse(manifest.isIgnoreFlags());
		assertEquals(3, manifest.getPlatformCommand().size());
		assertEquals("linux", manifest.getPlatformCommand().get(0).getOs());
		assertEquals("amd64", manifest.getPlatformCommand().get(0).getArch());
		assertNotNull(manifest.getHooks());
		assertEquals("$HELM_PLUGIN_DIR/install-binary.sh", manifest.getHooks().getInstall());
	}

	@Test
	void parsesDownloaderPluginManifest() throws Exception {
		HelmPluginManifest manifest = parse("/helm-plugins/s3/plugin.yaml");
		assertEquals("s3", manifest.getName());
		assertEquals(1, manifest.getDownloaders().size());
		assertTrue(manifest.getDownloaders().get(0).getProtocols().contains("s3"));
		assertEquals("$HELM_PLUGIN_DIR/bin/helms3", manifest.getDownloaders().get(0).getCommand());
	}

	@Test
	void ignoresUnknownFields() throws Exception {
		HelmPluginManifest manifest = this.yaml.readValue("""
				name: future
				version: 1.0.0
				command: run
				somethingHelmAddsLater: true
				platformHooks:
				  install:
				    - command: ["echo"]
				""", HelmPluginManifest.class);
		assertEquals("future", manifest.getName());
		assertEquals("run", manifest.getCommand());
	}

}
