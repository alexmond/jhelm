package org.alexmond.jhelm.core.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LifecyclePhaseTest {

	@Test
	void testWireValues() {
		assertEquals("pre-install", LifecyclePhase.PRE_INSTALL.getValue());
		assertEquals("post-install", LifecyclePhase.POST_INSTALL.getValue());
		assertEquals("pre-upgrade", LifecyclePhase.PRE_UPGRADE.getValue());
		assertEquals("post-upgrade", LifecyclePhase.POST_UPGRADE.getValue());
		assertEquals("pre-rollback", LifecyclePhase.PRE_ROLLBACK.getValue());
		assertEquals("post-rollback", LifecyclePhase.POST_ROLLBACK.getValue());
		assertEquals("pre-delete", LifecyclePhase.PRE_DELETE.getValue());
		assertEquals("post-delete", LifecyclePhase.POST_DELETE.getValue());
	}

}
