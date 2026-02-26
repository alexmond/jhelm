package org.alexmond.jhelm.plugin.service;

import java.io.File;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.plugin.adapter.WasmDownloaderAdapter;
import org.alexmond.jhelm.plugin.adapter.WasmLifecycleHookAdapter;
import org.alexmond.jhelm.plugin.adapter.WasmPostRendererAdapter;
import org.alexmond.jhelm.plugin.exception.PluginException;
import org.alexmond.jhelm.plugin.exception.PluginLoadException;
import org.alexmond.jhelm.plugin.exception.PluginNotFoundException;
import org.alexmond.jhelm.plugin.model.PluginDescriptor;
import org.alexmond.jhelm.plugin.model.PluginManifest;
import org.alexmond.jhelm.plugin.model.PluginType;
import org.alexmond.jhelm.plugin.runtime.WasmPluginInstance;
import org.alexmond.jhelm.plugin.runtime.WasmRuntime;
import org.alexmond.jhelm.plugin.sandbox.SandboxConfig;
import org.alexmond.jhelm.plugin.sandbox.SandboxedExecutor;
import org.alexmond.jhelm.plugin.spi.DownloaderPlugin;
import org.alexmond.jhelm.plugin.spi.LifecycleHookPlugin;
import org.alexmond.jhelm.plugin.spi.Plugin;
import org.alexmond.jhelm.plugin.spi.PostRendererPlugin;

/**
 * Top-level service for plugin discovery, loading, and lifecycle management.
 */
@Slf4j
@RequiredArgsConstructor
public class PluginManager {

	private final PluginRegistry registry;

	private final PluginLoader loader;

	private final WasmRuntime wasmRuntime;

	private final SandboxedExecutor sandboxedExecutor;

	/**
	 * Install a plugin from a {@code .jhp} archive file.
	 * @param pluginArchive the archive file
	 * @return the plugin descriptor
	 * @throws PluginException if installation fails
	 */
	public PluginDescriptor install(File pluginArchive) throws PluginException {
		PluginLoader.LoadResult result = loader.load(pluginArchive);
		PluginManifest manifest = result.manifest();
		WasmPluginInstance instance = wasmRuntime.load(manifest.getName(), result.wasmBytes());
		SandboxConfig sandboxConfig = buildSandboxConfig(manifest);
		Plugin plugin = createAdapter(manifest, instance, sandboxConfig);
		PluginDescriptor descriptor = PluginDescriptor.builder().manifest(manifest).plugin(plugin).build();
		registry.register(descriptor);
		log.info("Installed plugin: {} (type: {})", manifest.getName(), manifest.getType());
		return descriptor;
	}

	/**
	 * Uninstall a plugin by name.
	 * @param pluginName the plugin name
	 * @throws PluginNotFoundException if the plugin is not found
	 */
	public void uninstall(String pluginName) throws PluginNotFoundException {
		Optional<PluginDescriptor> descriptor = registry.unregister(pluginName);
		if (descriptor.isEmpty()) {
			throw new PluginNotFoundException("Plugin not found: " + pluginName);
		}
		descriptor.get().getPlugin().close();
		log.info("Uninstalled plugin: {}", pluginName);
	}

	/**
	 * List all installed plugins.
	 * @return list of descriptors
	 */
	public List<PluginDescriptor> list() {
		return registry.listAll();
	}

	/**
	 * Get all installed post-renderer plugins.
	 * @return list of post-renderer plugins
	 */
	public List<PostRendererPlugin> getPostRenderers() {
		return registry.listByType(PluginType.POST_RENDERER)
			.stream()
			.map((d) -> (PostRendererPlugin) d.getPlugin())
			.toList();
	}

	/**
	 * Get a downloader plugin that supports the given protocol.
	 * @param protocol the protocol string
	 * @return the matching plugin, or empty
	 */
	public Optional<DownloaderPlugin> getDownloaderFor(String protocol) {
		return registry.listByType(PluginType.DOWNLOADER)
			.stream()
			.map((d) -> (DownloaderPlugin) d.getPlugin())
			.filter((p) -> p.supportsProtocol(protocol))
			.findFirst();
	}

	/**
	 * Get all lifecycle hook plugins.
	 * @return list of lifecycle hook plugins
	 */
	public List<LifecycleHookPlugin> getLifecycleHooks() {
		return registry.listByType(PluginType.LIFECYCLE_HOOK)
			.stream()
			.map((d) -> (LifecycleHookPlugin) d.getPlugin())
			.toList();
	}

	private Plugin createAdapter(PluginManifest manifest, WasmPluginInstance instance, SandboxConfig config)
			throws PluginLoadException {
		return switch (manifest.getType()) {
			case POST_RENDERER -> new WasmPostRendererAdapter(manifest.getName(), instance, sandboxedExecutor, config);
			case DOWNLOADER -> new WasmDownloaderAdapter(manifest.getName(), instance, sandboxedExecutor, config);
			case LIFECYCLE_HOOK ->
				new WasmLifecycleHookAdapter(manifest.getName(), instance, sandboxedExecutor, config);
		};
	}

	private SandboxConfig buildSandboxConfig(PluginManifest manifest) {
		SandboxConfig.SandboxConfigBuilder builder = SandboxConfig.builder();
		if (manifest.getWasm() != null) {
			builder.timeoutSeconds(manifest.getWasm().getTimeoutSeconds());
			builder.memoryLimitPages(manifest.getWasm().getMemoryLimitPages());
		}
		return builder.build();
	}

}
