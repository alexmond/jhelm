package org.alexmond.jhelm.plugin.service;

import java.io.File;
import java.util.List;
import java.util.Optional;

import org.alexmond.jhelm.plugin.exception.PluginNotFoundException;
import org.alexmond.jhelm.plugin.model.PluginDescriptor;
import org.alexmond.jhelm.plugin.model.PluginManifest;
import org.alexmond.jhelm.plugin.model.PluginType;
import org.alexmond.jhelm.plugin.runtime.WasmPluginInstance;
import org.alexmond.jhelm.plugin.runtime.WasmRuntime;
import org.alexmond.jhelm.plugin.sandbox.SandboxedExecutor;
import org.alexmond.jhelm.plugin.spi.DownloaderPlugin;
import org.alexmond.jhelm.plugin.spi.LifecycleHookPlugin;
import org.alexmond.jhelm.plugin.spi.Plugin;
import org.alexmond.jhelm.plugin.spi.PostRendererPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginManagerTest {

	@Mock
	private PluginLoader loader;

	@Mock
	private WasmRuntime wasmRuntime;

	private PluginRegistry registry;

	private SandboxedExecutor sandboxedExecutor;

	private PluginManager manager;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		registry = new PluginRegistry();
		sandboxedExecutor = new SandboxedExecutor();
		manager = new PluginManager(registry, loader, wasmRuntime, sandboxedExecutor);
	}

	@Test
	void installLoadsAndRegistersPlugin() throws Exception {
		PluginManifest manifest = PluginManifest.builder()
			.name("my-renderer")
			.type(PluginType.POST_RENDERER)
			.version("1.0.0")
			.build();
		byte[] wasmBytes = new byte[] { 0x00, 0x61 };
		WasmPluginInstance instance = mock(WasmPluginInstance.class);

		when(loader.load(any(File.class))).thenReturn(new PluginLoader.LoadResult(manifest, wasmBytes));
		when(wasmRuntime.load(anyString(), any(byte[].class))).thenReturn(instance);

		PluginDescriptor result = manager.install(new File("test.jhp"));

		assertNotNull(result);
		assertEquals("my-renderer", result.getManifest().getName());
		assertEquals(PluginType.POST_RENDERER, result.getManifest().getType());
		assertTrue(registry.get("my-renderer").isPresent());
	}

	@Test
	void uninstallRemovesRegisteredPlugin() throws Exception {
		// Pre-register a plugin
		PluginManifest manifest = PluginManifest.builder()
			.name("to-remove")
			.type(PluginType.DOWNLOADER)
			.version("1.0.0")
			.build();
		Plugin plugin = mock(Plugin.class);
		registry.register(PluginDescriptor.builder().manifest(manifest).plugin(plugin).build());

		manager.uninstall("to-remove");

		assertTrue(registry.get("to-remove").isEmpty());
	}

	@Test
	void uninstallThrowsForUnknownPlugin() {
		assertThrows(PluginNotFoundException.class, () -> manager.uninstall("nonexistent"));
	}

	@Test
	void listReturnsAllRegistered() {
		registerStub("a", PluginType.POST_RENDERER);
		registerStub("b", PluginType.DOWNLOADER);

		List<PluginDescriptor> result = manager.list();
		assertEquals(2, result.size());
	}

	@Test
	void getPostRenderersReturnsOnlyPostRenderers() throws Exception {
		PluginManifest manifest = PluginManifest.builder()
			.name("renderer")
			.type(PluginType.POST_RENDERER)
			.version("1.0.0")
			.build();
		byte[] wasmBytes = new byte[] { 0x00 };
		WasmPluginInstance instance = mock(WasmPluginInstance.class);

		when(loader.load(any(File.class))).thenReturn(new PluginLoader.LoadResult(manifest, wasmBytes));
		when(wasmRuntime.load(anyString(), any(byte[].class))).thenReturn(instance);

		manager.install(new File("renderer.jhp"));

		List<PostRendererPlugin> renderers = manager.getPostRenderers();
		assertEquals(1, renderers.size());
	}

	@Test
	void getLifecycleHooksReturnsOnlyHooks() throws Exception {
		PluginManifest manifest = PluginManifest.builder()
			.name("hook")
			.type(PluginType.LIFECYCLE_HOOK)
			.version("1.0.0")
			.build();
		byte[] wasmBytes = new byte[] { 0x00 };
		WasmPluginInstance instance = mock(WasmPluginInstance.class);

		when(loader.load(any(File.class))).thenReturn(new PluginLoader.LoadResult(manifest, wasmBytes));
		when(wasmRuntime.load(anyString(), any(byte[].class))).thenReturn(instance);

		manager.install(new File("hook.jhp"));

		List<LifecycleHookPlugin> hooks = manager.getLifecycleHooks();
		assertEquals(1, hooks.size());
	}

	@Test
	void getDownloaderForReturnsEmpty() {
		Optional<DownloaderPlugin> result = manager.getDownloaderFor("custom://");
		assertTrue(result.isEmpty());
	}

	private void registerStub(String name, PluginType type) {
		PluginManifest manifest = PluginManifest.builder().name(name).type(type).version("1.0.0").build();
		Plugin plugin = mock(Plugin.class);
		when(plugin.name()).thenReturn(name);
		when(plugin.type()).thenReturn(type);
		registry.register(PluginDescriptor.builder().manifest(manifest).plugin(plugin).build());
	}

}
