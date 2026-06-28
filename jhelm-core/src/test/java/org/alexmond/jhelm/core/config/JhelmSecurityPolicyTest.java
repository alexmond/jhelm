package org.alexmond.jhelm.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the deny-by-default semantics of {@link JhelmSecurityPolicy}: mutating
 * operations are enabled only when the mode is {@link JhelmAccessMode#FULL FULL}
 * <em>and</em> an API key is configured, and API-key validation is correct.
 */
class JhelmSecurityPolicyTest {

	private static JhelmSecurityPolicy policy(JhelmAccessMode mode, String apiKey) {
		JhelmSecurityProperties props = new JhelmSecurityProperties();
		props.setMode(mode);
		props.setApiKey(apiKey);
		return new JhelmSecurityPolicy(props);
	}

	@Test
	void readOnlyNeverEnablesMutating() {
		assertFalse(policy(JhelmAccessMode.READ_ONLY, null).mutatingEnabled());
		assertFalse(policy(JhelmAccessMode.READ_ONLY, "secret").mutatingEnabled(),
				"READ_ONLY must stay read-only even with a key");
	}

	@Test
	void fullWithoutKeyDoesNotEnableMutating() {
		assertFalse(policy(JhelmAccessMode.FULL, null).mutatingEnabled(), "FULL without a key must stay disabled");
		assertFalse(policy(JhelmAccessMode.FULL, "   ").mutatingEnabled(), "FULL with a blank key must stay disabled");
	}

	@Test
	void fullWithKeyEnablesMutating() {
		assertTrue(policy(JhelmAccessMode.FULL, "secret").mutatingEnabled());
	}

	@Test
	void apiKeyConfiguredReflectsKeyPresence() {
		assertFalse(policy(JhelmAccessMode.FULL, null).apiKeyConfigured());
		assertFalse(policy(JhelmAccessMode.FULL, "").apiKeyConfigured());
		assertFalse(policy(JhelmAccessMode.FULL, "  ").apiKeyConfigured());
		assertTrue(policy(JhelmAccessMode.FULL, "secret").apiKeyConfigured());
	}

	@Test
	void validApiKeyMatchesOnlyCorrectKey() {
		JhelmSecurityPolicy policy = policy(JhelmAccessMode.FULL, "secret");
		assertTrue(policy.validApiKey("secret"));
		assertFalse(policy.validApiKey("wrong"));
		assertFalse(policy.validApiKey(""));
		assertFalse(policy.validApiKey(null));
	}

	@Test
	void validApiKeyFalseWhenNoKeyConfigured() {
		JhelmSecurityPolicy policy = policy(JhelmAccessMode.FULL, null);
		assertFalse(policy.validApiKey("anything"));
		assertFalse(policy.validApiKey(null));
	}

	@Test
	void exposesModeAndHeaderName() {
		JhelmSecurityProperties props = new JhelmSecurityProperties();
		props.setMode(JhelmAccessMode.FULL);
		props.setApiKeyHeader("X-Custom-Key");
		JhelmSecurityPolicy policy = new JhelmSecurityPolicy(props);
		assertEquals(JhelmAccessMode.FULL, policy.mode());
		assertEquals("X-Custom-Key", policy.apiKeyHeader());
	}

}
