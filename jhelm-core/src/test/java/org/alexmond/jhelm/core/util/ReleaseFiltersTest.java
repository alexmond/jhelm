package org.alexmond.jhelm.core.util;

import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.model.Release;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseFiltersTest {

	private static Release release(String name, Map<String, String> labels) {
		return Release.builder().name(name).labels(labels).build();
	}

	private static List<Release> sample() {
		return List.of(release("alpha", Map.of("team", "payments", "env", "prod")),
				release("beta", Map.of("team", "search")), release("gamma", Map.of("team", "payments", "env", "dev")),
				release("delta", null));
	}

	@Test
	void noCriteriaReturnsAllSortedByName() {
		List<Release> result = ReleaseFilters.apply(sample(), null, null, 0, 0);
		assertEquals(List.of("alpha", "beta", "delta", "gamma"), result.stream().map(Release::getName).toList());
	}

	@Test
	void selectorEqualityFiltersByLabel() {
		List<Release> result = ReleaseFilters.apply(sample(), "team=payments", null, 0, 0);
		assertEquals(List.of("alpha", "gamma"), result.stream().map(Release::getName).toList());
	}

	@Test
	void selectorAndsMultipleTerms() {
		List<Release> result = ReleaseFilters.apply(sample(), "team=payments,env=prod", null, 0, 0);
		assertEquals(List.of("alpha"), result.stream().map(Release::getName).toList());
	}

	@Test
	void selectorInequalityExcludesMatchingAndMissingLabels() {
		// env!=prod keeps releases whose env is not "prod" (including those with no env
		// label)
		List<Release> result = ReleaseFilters.apply(sample(), "env!=prod", null, 0, 0);
		assertEquals(List.of("beta", "delta", "gamma"), result.stream().map(Release::getName).toList());
	}

	@Test
	void doubleEqualsIsTreatedAsEquality() {
		List<Release> result = ReleaseFilters.apply(sample(), "team==search", null, 0, 0);
		assertEquals(List.of("beta"), result.stream().map(Release::getName).toList());
	}

	@Test
	void filterMatchesNameRegexUnanchored() {
		List<Release> result = ReleaseFilters.apply(sample(), null, "a$", 0, 0);
		assertEquals(List.of("alpha", "beta", "delta", "gamma"), result.stream().map(Release::getName).toList());
	}

	@Test
	void filterRegexNarrows() {
		List<Release> result = ReleaseFilters.apply(sample(), null, "^(alpha|beta)$", 0, 0);
		assertEquals(List.of("alpha", "beta"), result.stream().map(Release::getName).toList());
	}

	@Test
	void offsetAndMaxPaginateAfterSorting() {
		List<Release> result = ReleaseFilters.apply(sample(), null, null, 1, 2);
		assertEquals(List.of("beta", "delta"), result.stream().map(Release::getName).toList());
	}

	@Test
	void maxZeroMeansNoLimit() {
		List<Release> result = ReleaseFilters.apply(sample(), null, null, 0, 0);
		assertEquals(4, result.size());
	}

	@Test
	void negativeOffsetTreatedAsZero() {
		List<Release> result = ReleaseFilters.apply(sample(), null, null, -5, 0);
		assertEquals(4, result.size());
	}

	@Test
	void invalidSelectorTermThrows() {
		assertThrows(IllegalArgumentException.class, () -> ReleaseFilters.apply(sample(), "bogusterm", null, 0, 0));
	}

}
