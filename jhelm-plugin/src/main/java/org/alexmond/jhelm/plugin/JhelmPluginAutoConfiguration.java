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

	@Bean
	@ConditionalOnMissingBean
	public HostFunctionBridge hostFunctionBridge() {
		return new HostFunctionBridge();
	}

	@Bean
	@ConditionalOnMissingBean
	public WasmRuntime wasmRuntime(HostFunctionBridge hostFunctionBridge) {
		return new WasmRuntime(hostFunctionBridge);
	}

	@Bean
	@ConditionalOnMissingBean
	public SandboxedExecutor sandboxedExecutor() {
		return new SandboxedExecutor();
	}

	@Bean
	@ConditionalOnMissingBean
	public PluginLoader pluginLoader() {
		return new PluginLoader();
	}

	@Bean
	@ConditionalOnMissingBean
	public PluginRegistry pluginRegistry() {
		return new PluginRegistry();
	}

	@Bean
	@ConditionalOnMissingBean
	public PluginManager pluginManager(PluginRegistry registry, PluginLoader loader, WasmRuntime runtime,
			SandboxedExecutor executor) {
		return new PluginManager(registry, loader, runtime, executor);
	}

}
