package org.alexmond.jhelm.plugin.model;

import org.alexmond.jhelm.plugin.spi.Plugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class PluginDescriptorTest {

	@Test
	void builderCreatesDescriptor() {
		PluginManifest manifest = PluginManifest.builder()
			.name("test")
			.type(PluginType.LIFECYCLE_HOOK)
			.version("2.0.0")
			.build();
		Plugin plugin = mock(Plugin.class);

		PluginDescriptor descriptor = PluginDescriptor.builder().manifest(manifest).plugin(plugin).build();

		assertNotNull(descriptor.getManifest());
		assertNotNull(descriptor.getPlugin());
		assertEquals("test", descriptor.getManifest().getName());
		assertEquals(PluginType.LIFECYCLE_HOOK, descriptor.getManifest().getType());
	}

}
