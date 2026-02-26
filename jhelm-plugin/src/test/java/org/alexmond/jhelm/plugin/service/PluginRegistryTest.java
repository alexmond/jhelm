package org.alexmond.jhelm.plugin.service;

import java.util.List;
import java.util.Optional;

import org.alexmond.jhelm.plugin.model.PluginDescriptor;
import org.alexmond.jhelm.plugin.model.PluginManifest;
import org.alexmond.jhelm.plugin.model.PluginType;
import org.alexmond.jhelm.plugin.spi.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRegistryTest {

	private PluginRegistry registry;

	@BeforeEach
	void setUp() {
		registry = new PluginRegistry();
	}

	@Test
	void registerAndGetPlugin() {
		PluginDescriptor descriptor = createDescriptor("test-plugin", PluginType.POST_RENDERER);
		registry.register(descriptor);

		Optional<PluginDescriptor> result = registry.get("test-plugin");
		assertTrue(result.isPresent());
		assertEquals("test-plugin", result.get().getManifest().getName());
	}

	@Test
	void getReturnsEmptyForUnknownPlugin() {
		assertFalse(registry.get("nonexistent").isPresent());
	}

	@Test
	void unregisterRemovesPlugin() {
		PluginDescriptor descriptor = createDescriptor("test-plugin", PluginType.DOWNLOADER);
		registry.register(descriptor);

		Optional<PluginDescriptor> removed = registry.unregister("test-plugin");
		assertTrue(removed.isPresent());
		assertFalse(registry.get("test-plugin").isPresent());
	}

	@Test
	void unregisterReturnsEmptyForUnknownPlugin() {
		assertFalse(registry.unregister("nonexistent").isPresent());
	}

	@Test
	void listAllReturnsAllRegistered() {
		registry.register(createDescriptor("plugin-a", PluginType.POST_RENDERER));
		registry.register(createDescriptor("plugin-b", PluginType.DOWNLOADER));
		registry.register(createDescriptor("plugin-c", PluginType.LIFECYCLE_HOOK));

		List<PluginDescriptor> all = registry.listAll();
		assertEquals(3, all.size());
	}

	@Test
	void listAllReturnsEmptyWhenNoPlugins() {
		assertTrue(registry.listAll().isEmpty());
	}

	@Test
	void listByTypeFiltersCorrectly() {
		registry.register(createDescriptor("renderer-1", PluginType.POST_RENDERER));
		registry.register(createDescriptor("renderer-2", PluginType.POST_RENDERER));
		registry.register(createDescriptor("downloader-1", PluginType.DOWNLOADER));
		registry.register(createDescriptor("hook-1", PluginType.LIFECYCLE_HOOK));

		assertEquals(2, registry.listByType(PluginType.POST_RENDERER).size());
		assertEquals(1, registry.listByType(PluginType.DOWNLOADER).size());
		assertEquals(1, registry.listByType(PluginType.LIFECYCLE_HOOK).size());
	}

	@Test
	void registerOverwritesExistingPlugin() {
		PluginDescriptor first = createDescriptor("same-name", PluginType.POST_RENDERER);
		PluginDescriptor second = createDescriptor("same-name", PluginType.DOWNLOADER);

		registry.register(first);
		registry.register(second);

		Optional<PluginDescriptor> result = registry.get("same-name");
		assertTrue(result.isPresent());
		assertEquals(PluginType.DOWNLOADER, result.get().getManifest().getType());
		assertEquals(1, registry.listAll().size());
	}

	private PluginDescriptor createDescriptor(String name, PluginType type) {
		PluginManifest manifest = PluginManifest.builder().name(name).type(type).version("1.0.0").build();
		Plugin plugin = new StubPlugin(name, type);
		return PluginDescriptor.builder().manifest(manifest).plugin(plugin).build();
	}

	private record StubPlugin(String pluginName, PluginType pluginType) implements Plugin {

		@Override
		public String name() {
			return pluginName;
		}

		@Override
		public PluginType type() {
			return pluginType;
		}

		@Override
		public void close() {
		}

	}

}
