package org.alexmond.jhelm.plugin;

import org.alexmond.jhelm.plugin.config.JhelmPluginProperties;
import org.alexmond.jhelm.plugin.runtime.HostFunctionBridge;
import org.alexmond.jhelm.plugin.runtime.WasmRuntime;
import org.alexmond.jhelm.plugin.sandbox.SandboxedExecutor;
import org.alexmond.jhelm.plugin.service.PluginLoader;
import org.alexmond.jhelm.plugin.service.PluginManager;
import org.alexmond.jhelm.plugin.service.PluginRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the jhelm plugin system. Only activated when
 * {@code jhelm.plugins.enabled=true}.
 */
@AutoConfiguration
@EnableConfigurationProperties(JhelmPluginProperties.class)
@ConditionalOnProperty(name = "jhelm.plugins.enabled", havingValue = "true")
public class JhelmPluginAutoConfiguration {

	/**
	 * Provide the bridge that exposes host functions to WASM plugins.
	 * @return the host function bridge bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public HostFunctionBridge hostFunctionBridge() {
		return new HostFunctionBridge();
	}

	/**
	 * Provide the Chicory-based WASM runtime.
	 * @param hostFunctionBridge the host functions made available to plugins
	 * @return the WASM runtime bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public WasmRuntime wasmRuntime(HostFunctionBridge hostFunctionBridge) {
		return new WasmRuntime(hostFunctionBridge);
	}

	/**
	 * Provide the executor that enforces per-plugin timeout limits.
	 * @return the sandboxed executor bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public SandboxedExecutor sandboxedExecutor() {
		return new SandboxedExecutor();
	}

	/**
	 * Provide the loader that reads plugin archives.
	 * @return the plugin loader bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public PluginLoader pluginLoader() {
		return new PluginLoader();
	}

	/**
	 * Provide the in-memory registry of loaded plugins.
	 * @return the plugin registry bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public PluginRegistry pluginRegistry() {
		return new PluginRegistry();
	}

	/**
	 * Provide the top-level plugin manager that ties the registry, loader, runtime, and
	 * executor together.
	 * @param registry the plugin registry
	 * @param loader the plugin loader
	 * @param runtime the WASM runtime
	 * @param executor the sandboxed executor
	 * @return the plugin manager bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public PluginManager pluginManager(PluginRegistry registry, PluginLoader loader, WasmRuntime runtime,
			SandboxedExecutor executor) {
		return new PluginManager(registry, loader, runtime, executor);
	}

}
