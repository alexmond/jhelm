package org.alexmond.jhelm.app.plugin;

import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelmPluginExecGuardTest {

	private static JhelmSecurityPolicy policy(JhelmAccessMode mode) {
		JhelmSecurityProperties properties = new JhelmSecurityProperties();
		properties.setMode(mode);
		return new JhelmSecurityPolicy(properties);
	}

	@Test
	void fullModeAllowsPluginExecution() {
		assertFalse(HelmPluginExecGuard.blocked(policy(JhelmAccessMode.FULL)));
	}

	@Test
	void readOnlyModeBlocksPluginExecution() {
		assertTrue(HelmPluginExecGuard.blocked(policy(JhelmAccessMode.READ_ONLY)));
	}

}
