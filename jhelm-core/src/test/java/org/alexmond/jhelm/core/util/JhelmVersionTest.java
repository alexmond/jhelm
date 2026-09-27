package org.alexmond.jhelm.core.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JhelmVersionTest {

	@Test
	void currentIsTheFilteredProjectVersion() {
		String version = JhelmVersion.current();
		// the build filters jhelm-version.properties; an unfiltered copy would leak the
		// placeholder or fall through to the development marker
		assertFalse(version.contains("@"), version);
		assertFalse(JhelmVersion.DEVELOPMENT.equals(version), version);
	}

	@ParameterizedTest
	@CsvSource({ "1.5.1, v1.5.1", "v1.5.1, v1.5.1", "' 1.5.1-SNAPSHOT ', v1.5.1-SNAPSHOT" })
	void withLeadingVNormalises(String input, String expected) {
		assertEquals(expected, JhelmVersion.withLeadingV(input));
	}

}
