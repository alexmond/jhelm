package org.alexmond.jhelm.plugin.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginTypeTest {

	@Test
	void getValueReturnsCorrectString() {
		assertEquals("postrenderer", PluginType.POST_RENDERER.getValue());
		assertEquals("downloader", PluginType.DOWNLOADER.getValue());
		assertEquals("lifecyclehook", PluginType.LIFECYCLE_HOOK.getValue());
	}

	@Test
	void fromValueParsesKnownTypes() {
		assertEquals(PluginType.POST_RENDERER, PluginType.fromValue("postrenderer"));
		assertEquals(PluginType.DOWNLOADER, PluginType.fromValue("downloader"));
		assertEquals(PluginType.LIFECYCLE_HOOK, PluginType.fromValue("lifecyclehook"));
	}

	@Test
	void fromValueIsCaseInsensitive() {
		assertEquals(PluginType.POST_RENDERER, PluginType.fromValue("PostRenderer"));
		assertEquals(PluginType.DOWNLOADER, PluginType.fromValue("DOWNLOADER"));
	}

	@Test
	void fromValueThrowsForUnknownType() {
		assertThrows(IllegalArgumentException.class, () -> PluginType.fromValue("unknown"));
	}

}
