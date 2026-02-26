package org.alexmond.jhelm.plugin.model;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PluginEventTest {

	@Test
	void builderCreatesEvent() {
		PluginEvent event = PluginEvent.builder()
			.phase("pre-install")
			.releaseName("my-release")
			.namespace("default")
			.metadata(Map.of("key", "value"))
			.build();

		assertEquals("pre-install", event.getPhase());
		assertEquals("my-release", event.getReleaseName());
		assertEquals("default", event.getNamespace());
		assertNotNull(event.getMetadata());
		assertEquals("value", event.getMetadata().get("key"));
	}

}
