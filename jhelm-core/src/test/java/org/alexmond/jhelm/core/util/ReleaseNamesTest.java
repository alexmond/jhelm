package org.alexmond.jhelm.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseNamesTest {

	@Test
	void generateNameUsesChartNameAndTimestamp() {
		String name = ReleaseNames.generateName("mychart");
		assertTrue(name.matches("mychart-\\d+"), name);
		assertDoesNotThrow(() -> ReleaseNames.validateReleaseName(name));
	}

	@Test
	void generateNameSanitizesAndBoundsLength() {
		String name = ReleaseNames.generateName("My_Very-LONG.Chart_Name_" + "x".repeat(80));
		assertTrue(name.length() <= 53, name);
		assertDoesNotThrow(() -> ReleaseNames.validateReleaseName(name));
	}

	@Test
	void generateNameFallsBackWhenChartNameBlank() {
		assertTrue(ReleaseNames.generateName("  ").matches("release-\\d+"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "myrelease", "release-grafana-grafana", "a", "r1.2.3", "prom-1" })
	void acceptsValidReleaseNames(String name) {
		assertDoesNotThrow(() -> ReleaseNames.validateReleaseName(name));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "-bad", "bad-", "Upper", "with space", "a/b", "a..b/../c", "name_underscore" })
	void rejectsInvalidReleaseNames(String name) {
		assertThrows(IllegalArgumentException.class, () -> ReleaseNames.validateReleaseName(name));
	}

	@Test
	void rejectsTooLongReleaseName() {
		assertThrows(IllegalArgumentException.class, () -> ReleaseNames.validateReleaseName("a".repeat(54)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "default", "kube-system", "ns1", "a" })
	void acceptsValidNamespaces(String ns) {
		assertDoesNotThrow(() -> ReleaseNames.validateNamespace(ns));
	}

	@Test
	void allowsBlankNamespaceAsDefault() {
		assertDoesNotThrow(() -> ReleaseNames.validateNamespace(null));
		assertDoesNotThrow(() -> ReleaseNames.validateNamespace(""));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Bad", "ns_1", "a/b", "-ns", "ns-", "with.dot" })
	void rejectsInvalidNamespaces(String ns) {
		assertThrows(IllegalArgumentException.class, () -> ReleaseNames.validateNamespace(ns));
	}

}
