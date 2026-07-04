package org.alexmond.jhelm.core.util;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the profile-expression grammar behind {@code spring.config.activate.on-profile}.
 * The expected results mirror what a live Spring Cloud Config Server was observed to do
 * server-side (`prod | staging`, `prod & cloud`, `!test`, comma-lists), so a directive
 * resolves identically for local files and config-server-sourced values.
 */
class ValuesProfilesTest {

	@ParameterizedTest
	@CsvSource(delimiter = ';', value = {
			// activeCsv ; expression ; expectedMatch
			"prod            ; prod              ; true", "prod            ; staging           ; false",
			"prod            ; prod | staging    ; true", "staging         ; prod | staging    ; true",
			"dev             ; prod | staging    ; false", "prod            ; prod,staging      ; true",
			"staging         ; prod,staging      ; true", "dev             ; prod,staging      ; false",
			"'prod,cloud'    ; prod & cloud      ; true", "prod            ; prod & cloud      ; false",
			"dev             ; '!test'           ; true", "test            ; '!test'           ; false",
			"''              ; '!test'           ; true", "'prod,cloud'    ; prod & !test      ; true",
			"'prod,test'     ; prod & !test      ; false", "prod            ; (prod | dev) & !test ; true",
			"test            ; (prod | dev) & !test ; false", "prod            ; PROD              ; false" })
	void testExpressionMatching(String activeCsv, String expression, boolean expected) {
		ValuesProfiles profiles = ValuesProfiles.of(List.of(activeCsv.split(",", -1)));
		assertEquals(expected, profiles.matches(expression), "active=[" + activeCsv + "] expr=<" + expression + ">");
	}

	@Test
	void testNullAndBlankExpressionAlwaysMatch() {
		ValuesProfiles profiles = ValuesProfiles.of(List.of());
		assertTrue(profiles.matches(null), "no directive -> always applies");
		assertTrue(profiles.matches("   "), "blank directive -> always applies");
	}

	@Test
	void testActiveOrderAndDedupPreservedForLastWins() {
		// "--profile a,b" and "--profile a --profile b" both -> [a, b], order preserved.
		assertEquals(List.of("a", "b"), ValuesProfiles.of(List.of("a,b")).active());
		assertEquals(List.of("a", "b"), ValuesProfiles.of(List.of("a", "b")).active());
		// duplicates collapse, first-seen order kept
		assertEquals(List.of("a", "b"), ValuesProfiles.of(List.of("a", "b", "a")).active());
	}

	@Test
	void testEmptyProfiles() {
		assertTrue(ValuesProfiles.none().isEmpty());
		assertTrue(ValuesProfiles.of(null).isEmpty());
		assertTrue(ValuesProfiles.of(List.of("  ", "")).isEmpty());
		assertFalse(ValuesProfiles.of(List.of("prod")).isEmpty());
	}

	@Test
	void testInvalidExpressionThrows() {
		ValuesProfiles profiles = ValuesProfiles.of(List.of("prod"));
		assertThrows(IllegalArgumentException.class, () -> profiles.matches("prod &"));
		assertThrows(IllegalArgumentException.class, () -> profiles.matches("(prod"));
		assertThrows(IllegalArgumentException.class, () -> profiles.matches("prod dev"));
	}

}
