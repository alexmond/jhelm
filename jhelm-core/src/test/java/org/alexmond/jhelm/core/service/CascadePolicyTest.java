package org.alexmond.jhelm.core.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CascadePolicyTest {

	@Test
	void parsesKnownModesCaseInsensitively() {
		assertEquals(CascadePolicy.BACKGROUND, CascadePolicy.fromString("background"));
		assertEquals(CascadePolicy.FOREGROUND, CascadePolicy.fromString("Foreground"));
		assertEquals(CascadePolicy.ORPHAN, CascadePolicy.fromString("ORPHAN"));
	}

	@Test
	void nullOrBlankDefaultsToBackground() {
		assertEquals(CascadePolicy.BACKGROUND, CascadePolicy.fromString(null));
		assertEquals(CascadePolicy.BACKGROUND, CascadePolicy.fromString("   "));
	}

	@Test
	void unknownModeIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> CascadePolicy.fromString("sideways"));
	}

	@Test
	void propagationPolicyMapsToKubernetesValues() {
		assertEquals("Background", CascadePolicy.BACKGROUND.propagationPolicy());
		assertEquals("Foreground", CascadePolicy.FOREGROUND.propagationPolicy());
		assertEquals("Orphan", CascadePolicy.ORPHAN.propagationPolicy());
	}

}
