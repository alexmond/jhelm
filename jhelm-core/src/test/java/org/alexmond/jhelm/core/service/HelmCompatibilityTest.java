package org.alexmond.jhelm.core.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelmCompatibilityTest {

	@ParameterizedTest
	@CsvSource({ "3, V3", "v3, V3", "helm3, V3", "V3, V3", "4, V4", "v4, V4", "' 4 ', V4" })
	void fromParsesTheHelmLine(String value, HelmCompatibility expected) {
		assertEquals(expected, HelmCompatibility.from(value));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void fromDefaultsToHelm4(String value) {
		assertEquals(HelmCompatibility.V4, HelmCompatibility.from(value));
	}

	@Test
	void fromNullDefaultsToHelm4() {
		assertEquals(HelmCompatibility.V4, HelmCompatibility.from(null));
	}

	@ParameterizedTest
	@ValueSource(strings = { "2", "5", "latest" })
	void fromRejectsUnknownLines(String value) {
		assertThrows(IllegalArgumentException.class, () -> HelmCompatibility.from(value));
	}

	@Test
	void onlyHelm4PrunesNullValues() {
		assertTrue(HelmCompatibility.V4.prunesNullValues());
		assertFalse(HelmCompatibility.V3.prunesNullValues());
	}

	@Test
	void eachModeCarriesItsDefaultVersion() {
		assertTrue(HelmCompatibility.V4.defaultHelmVersion().startsWith("v4."));
		assertTrue(HelmCompatibility.V3.defaultHelmVersion().startsWith("v3."));
	}

}
