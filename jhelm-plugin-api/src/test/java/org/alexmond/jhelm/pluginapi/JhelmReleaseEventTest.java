package org.alexmond.jhelm.pluginapi;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JhelmReleaseEventTest {

	@Test
	void nullMetadataDefaultsToEmptyMap() {
		JhelmReleaseEvent event = new JhelmReleaseEvent(JhelmReleaseEvent.Phase.PRE_INSTALL, "rel", "ns", null);
		assertTrue(event.metadata().isEmpty());
	}

	@Test
	void metadataIsCopiedAndImmutable() {
		JhelmReleaseEvent event = new JhelmReleaseEvent(JhelmReleaseEvent.Phase.POST_UPGRADE, "rel", "ns",
				Map.of("revision", 3));
		assertEquals(3, event.metadata().get("revision"));
		assertThrows(UnsupportedOperationException.class, () -> event.metadata().put("x", 1));
	}

}
