package org.alexmond.jhelm.plugin.config;

import org.alexmond.jhelm.plugin.JhelmPluginAutoConfiguration;
import org.alexmond.jhelm.plugin.runtime.HostFunctionBridge;
import org.alexmond.jhelm.plugin.runtime.WasmRuntime;
import org.alexmond.jhelm.plugin.sandbox.SandboxedExecutor;
import org.alexmond.jhelm.plugin.service.PluginLoader;
import org.alexmond.jhelm.plugin.service.PluginManager;
import org.alexmond.jhelm.plugin.service.PluginRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JhelmPluginAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JhelmPluginAutoConfiguration.class));

	@Test
	void beansNotRegisteredWhenDisabled() {
		contextRunner.run((ctx) -> {
			assertEquals(0, ctx.getBeanNamesForType(PluginManager.class).length);
			assertEquals(0, ctx.getBeanNamesForType(PluginRegistry.class).length);
			assertEquals(0, ctx.getBeanNamesForType(WasmRuntime.class).length);
		});
	}

	@Test
	void beansRegisteredWhenEnabled() {
		contextRunner.withPropertyValues("jhelm.plugins.enabled=true").run((ctx) -> {
			assertNotNull(ctx.getBean(PluginManager.class));
			assertNotNull(ctx.getBean(PluginRegistry.class));
			assertNotNull(ctx.getBean(PluginLoader.class));
			assertNotNull(ctx.getBean(WasmRuntime.class));
			assertNotNull(ctx.getBean(HostFunctionBridge.class));
			assertNotNull(ctx.getBean(SandboxedExecutor.class));
		});
	}

	@Test
	void beansNotRegisteredWhenEnabledFalse() {
		contextRunner.withPropertyValues("jhelm.plugins.enabled=false")
			.run((ctx) -> assertEquals(0, ctx.getBeanNamesForType(PluginManager.class).length));
	}

}
