package org.alexmond.jhelm.app.plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs({ OS.LINUX, OS.MAC })
class HelmPluginChartDownloaderTest {

	@TempDir
	Path pluginsDir;

	private void installDownloaderPlugin() throws IOException {
		Path dir = Files.createDirectories(this.pluginsDir.resolve("s3"));
		Files.writeString(dir.resolve("plugin.yaml"), """
				name: s3
				version: 1.0.0
				command: "$HELM_PLUGIN_DIR/dl.sh"
				downloaders:
				  - command: "$HELM_PLUGIN_DIR/dl.sh"
				    protocols:
				      - test
				""");
		// Emits chart bytes on stdout; the URL is the 4th arg (after cert/key/ca).
		Path dl = dir.resolve("dl.sh");
		Files.writeString(dl, "#!/usr/bin/env bash\nprintf 'CHART-FOR:%s' \"$4\"\n");
		dl.toFile().setExecutable(true);
	}

	private HelmPluginChartDownloader downloader(JhelmAccessMode mode) {
		JhelmSecurityProperties props = new JhelmSecurityProperties();
		props.setMode(mode);
		HelmPluginPaths paths = new HelmPluginPaths(Map.of("HELM_PLUGINS", this.pluginsDir.toString())::get,
				Path.of("/home/tester"));
		Supplier<HelmPluginEnvironment> env = () -> HelmPluginEnvironment.builder().paths(paths).build();
		return HelmPluginChartDownloader.forTesting(this.pluginsDir, env, new JhelmSecurityPolicy(props));
	}

	@Test
	void supportsDeclaredProtocolOnly() throws Exception {
		installDownloaderPlugin();
		assertTrue(downloader(JhelmAccessMode.FULL).supportsProtocol("test"));
		assertFalse(downloader(JhelmAccessMode.FULL).supportsProtocol("s3"));
		assertFalse(downloader(JhelmAccessMode.FULL).supportsProtocol("nope"));
	}

	@Test
	void downloadsChartBytesViaTheContract() throws Exception {
		installDownloaderPlugin();
		byte[] bytes = downloader(JhelmAccessMode.FULL).download("test://bucket/mychart-1.0.0.tgz");
		assertEquals("CHART-FOR:test://bucket/mychart-1.0.0.tgz", new String(bytes, StandardCharsets.UTF_8));
	}

	@Test
	void readOnlyModeRefusesDownload() throws Exception {
		installDownloaderPlugin();
		IOException ex = assertThrows(IOException.class,
				() -> downloader(JhelmAccessMode.READ_ONLY).download("test://bucket/x.tgz"));
		assertTrue(ex.getMessage().contains("READ_ONLY"));
	}

	@Test
	void unknownSchemeThrows() throws Exception {
		installDownloaderPlugin();
		IOException ex = assertThrows(IOException.class,
				() -> downloader(JhelmAccessMode.FULL).download("gs://bucket/x.tgz"));
		assertTrue(ex.getMessage().contains("gs"));
	}

}
