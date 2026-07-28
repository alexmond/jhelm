package org.alexmond.jhelm.core.util;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartVersionsTest {

	@ParameterizedTest
	@CsvSource({
			// v-prefix tolerance and numeric core comparison
			"5.3.0, v0.5.0, 1", "v1.2.0, 1.10.0, -1", "2.1.0, v2.1.0, 0", "v2.1.0, v2.1.0, 0",
			// numeric segments, not lexicographic
			"2.14.1, 2.2.4, 1", "2.2.4, 2.14.1, -1", "3.0.0, 2.99.99, 1",
			// missing trailing segment treated as zero
			"1.2.3, 1.2, 1", "1.0, 1.0.0, 0", "1.2, 1.2.0, 0",
			// release outranks its pre-release; pre-releases ordered lexicographically
			"1.0.0, 1.0.0-rc1, 1", "1.0.0-rc1, 1.0.0, -1", "1.0.0-rc2, 1.0.0-rc1, 1", "5.3.0, 5.3.0-alpha, 1",
			// equal
			"1.0.0, 1.0.0, 0" })
	void testCompareSign(String a, String b, int expectedSign) {
		assertEquals(expectedSign, Integer.signum(ChartVersions.compare(a, b)),
				"compare(\"%s\", \"%s\")".formatted(a, b));
	}

	@Test
	void testNullHandling() {
		assertEquals(0, ChartVersions.compare(null, null));
		assertTrue(ChartVersions.compare(null, "1.0.0") < 0, "null sorts before a real version");
		assertTrue(ChartVersions.compare("1.0.0", null) > 0, "a real version sorts after null");
	}

	@Test
	void testNonNumericSegmentsFallBackToLexical() {
		// A non-integer core segment can't be parsed numerically, so it is compared
		// lexicographically rather than throwing.
		assertTrue(ChartVersions.compare("1.beta.0", "1.alpha.0") > 0, "beta > alpha lexically");
		assertEquals(0, ChartVersions.compare("1.x.0", "1.x.0"), "identical non-numeric cores are equal");
	}

	@Test
	void testComparatorSortsAscendingNewestLast() {
		List<String> versions = new ArrayList<>(List.of("1.10.0", "v0.5.0", "1.2.0", "1.10.0-rc1", "1.10.0"));
		versions.sort(ChartVersions.COMPARATOR);
		assertEquals(List.of("v0.5.0", "1.2.0", "1.10.0-rc1", "1.10.0", "1.10.0"), versions);
	}

}
