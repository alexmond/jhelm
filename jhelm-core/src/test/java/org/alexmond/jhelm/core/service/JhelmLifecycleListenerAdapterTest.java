package org.alexmond.jhelm.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.pluginapi.JhelmLifecycleListener;
import org.alexmond.jhelm.pluginapi.JhelmReleaseEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JhelmLifecycleListenerAdapterTest {

	@Test
	void deliversMappedEventWithReleaseDetails() throws Exception {
		List<JhelmReleaseEvent> received = new ArrayList<>();
		LifecycleListener adapter = new JhelmLifecycleListenerAdapter(received::add);

		adapter.onEvent(LifecyclePhase.POST_INSTALL, "rel", "ns", Map.of("revision", 1));

		assertEquals(1, received.size());
		assertEquals(JhelmReleaseEvent.Phase.POST_INSTALL, received.get(0).phase());
		assertEquals("rel", received.get(0).releaseName());
		assertEquals("ns", received.get(0).namespace());
		assertEquals(1, received.get(0).metadata().get("revision"));
	}

	@Test
	void mapsDeletePhaseToUninstall() throws Exception {
		List<JhelmReleaseEvent> received = new ArrayList<>();
		LifecycleListener adapter = new JhelmLifecycleListenerAdapter(received::add);

		adapter.onEvent(LifecyclePhase.PRE_DELETE, "rel", "ns", Map.of());
		adapter.onEvent(LifecyclePhase.POST_ROLLBACK, "rel", "ns", Map.of());

		assertEquals(JhelmReleaseEvent.Phase.PRE_UNINSTALL, received.get(0).phase());
		assertEquals(JhelmReleaseEvent.Phase.POST_ROLLBACK, received.get(1).phase());
	}

	@Test
	void everyInternalPhaseMapsToAPublicPhase() throws Exception {
		List<JhelmReleaseEvent> received = new ArrayList<>();
		LifecycleListener adapter = new JhelmLifecycleListenerAdapter(received::add);
		for (LifecyclePhase phase : LifecyclePhase.values()) {
			adapter.onEvent(phase, "rel", "ns", Map.of());
		}
		assertEquals(LifecyclePhase.values().length, received.size(), "no phase dropped");
	}

	@Test
	void aThrowingListenerIsSwallowed() {
		JhelmLifecycleListener throwing = (event) -> {
			throw new IllegalStateException("boom");
		};
		LifecycleListener adapter = new JhelmLifecycleListenerAdapter(throwing);
		assertDoesNotThrow(() -> adapter.onEvent(LifecyclePhase.POST_UPGRADE, "rel", "ns", Map.of()));
	}

}
