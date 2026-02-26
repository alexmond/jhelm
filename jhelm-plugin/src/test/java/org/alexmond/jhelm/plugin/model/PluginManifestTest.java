package org.alexmond.jhelm.plugin.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PluginManifestTest {

	@Test
	void builderCreatesManifest() {
		PluginManifest manifest = PluginManifest.builder()
			.apiVersion("v1")
			.name("test-plugin")
			.version("1.0.0")
			.type(PluginType.POST_RENDERER)
			.description("A test plugin")
			.runtime("wasm")
			.build();

		assertEquals("v1", manifest.getApiVersion());
		assertEquals("test-plugin", manifest.getName());
		assertEquals("1.0.0", manifest.getVersion());
		assertEquals(PluginType.POST_RENDERER, manifest.getType());
		assertEquals("A test plugin", manifest.getDescription());
		assertEquals("wasm", manifest.getRuntime());
	}

	@Test
	void wasmConfigDefaults() {
		PluginManifest.WasmConfig config = PluginManifest.WasmConfig.builder().build();

		assertEquals(256, config.getMemoryLimitPages());
		assertEquals(30, config.getTimeoutSeconds());
		assertNull(config.getEntrypoint());
	}

	@Test
	void wasmConfigCustomValues() {
		PluginManifest.WasmConfig config = PluginManifest.WasmConfig.builder()
			.entrypoint("custom.wasm")
			.memoryLimitPages(128)
			.timeoutSeconds(60)
			.build();

		assertEquals("custom.wasm", config.getEntrypoint());
		assertEquals(128, config.getMemoryLimitPages());
		assertEquals(60, config.getTimeoutSeconds());
	}

	@Test
	void manifestWithWasmConfig() {
		PluginManifest manifest = PluginManifest.builder()
			.name("wasm-plugin")
			.type(PluginType.DOWNLOADER)
			.wasm(PluginManifest.WasmConfig.builder().entrypoint("download.wasm").timeoutSeconds(45).build())
			.build();

		assertNotNull(manifest.getWasm());
		assertEquals("download.wasm", manifest.getWasm().getEntrypoint());
		assertEquals(45, manifest.getWasm().getTimeoutSeconds());
	}

}
