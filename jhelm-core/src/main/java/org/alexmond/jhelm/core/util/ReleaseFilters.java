package org.alexmond.jhelm.core.util;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.alexmond.jhelm.core.model.Release;

/**
 * Client-side filtering and pagination for {@code helm list}, mirroring Helm's
 * {@code -l}/{@code --selector}, {@code --filter}, {@code --offset} and {@code --max}
 * flags. Applied to the releases already fetched from the cluster.
 */
public final class ReleaseFilters {

	private ReleaseFilters() {
	}

	/**
	 * Applies the label selector, name filter and pagination to a release list, in Helm's
	 * order: filter by selector, then by the name regex, sort by name, then apply
	 * {@code offset}/{@code max}.
	 * @param releases the releases to filter (not modified)
	 * @param selector a label selector ({@code k=v}, {@code k==v} or {@code k!=v} terms
	 * joined by commas, all ANDed); {@code null}/blank means no selector
	 * @param filter a regular expression matched against the release name (unanchored);
	 * {@code null}/blank means no name filter
	 * @param offset the number of leading releases to skip (negative treated as 0)
	 * @param max the maximum number of releases to return ({@code <= 0} means no limit)
	 * @return a new list with the surviving releases, sorted by name
	 */
	public static List<Release> apply(List<Release> releases, String selector, String filter, int offset, int max) {
		Map<String, Matcher> selectors = parseSelector(selector);
		Pattern namePattern = (filter != null && !filter.isBlank()) ? Pattern.compile(filter) : null;

		List<Release> result = releases.stream()
			.filter((r) -> matchesSelector(r, selectors))
			.filter((r) -> namePattern == null || (r.getName() != null && namePattern.matcher(r.getName()).find()))
			.sorted(Comparator.comparing(Release::getName, Comparator.nullsFirst(Comparator.naturalOrder())))
			.skip(Math.max(0, offset))
			.toList();

		if (max > 0 && result.size() > max) {
			return List.copyOf(result.subList(0, max));
		}
		return result;
	}

	private static Map<String, Matcher> parseSelector(String selector) {
		if (selector == null || selector.isBlank()) {
			return Map.of();
		}
		Map<String, Matcher> matchers = new LinkedHashMap<>();
		for (String term : selector.split(",")) {
			String t = term.trim();
			if (t.isEmpty()) {
				continue;
			}
			int neq = t.indexOf("!=");
			if (neq >= 0) {
				matchers.put(t.substring(0, neq).trim(), new Matcher(t.substring(neq + 2).trim(), false));
				continue;
			}
			int eq = t.indexOf('=');
			if (eq < 0) {
				throw new IllegalArgumentException(
						"Invalid selector term '" + t + "' (expected key=value or key!=value)");
			}
			// tolerate both key=value and key==value
			String value = t.substring(eq + 1);
			if (value.startsWith("=")) {
				value = value.substring(1);
			}
			matchers.put(t.substring(0, eq).trim(), new Matcher(value.trim(), true));
		}
		return matchers;
	}

	private static boolean matchesSelector(Release release, Map<String, Matcher> selectors) {
		if (selectors.isEmpty()) {
			return true;
		}
		Map<String, String> labels = release.getLabels();
		for (Map.Entry<String, Matcher> entry : selectors.entrySet()) {
			String actual = (labels != null) ? labels.get(entry.getKey()) : null;
			Matcher matcher = entry.getValue();
			boolean equal = matcher.value.equals(actual);
			if (matcher.wantEqual != equal) {
				return false;
			}
		}
		return true;
	}

	// A single selector term: the expected value and whether equality (=) or inequality
	// (!=).
	private record Matcher(String value, boolean wantEqual) {
	}

}
