package org.alexmond.jhelm.core.util;

import java.util.Comparator;

/**
 * Lenient chart-version comparison matching how Helm (and jhelm) select the "latest"
 * version of a chart.
 *
 * <p>
 * The semantics are deliberately tolerant of the version strings seen in real Helm chart
 * repositories:
 * <ul>
 * <li>A leading {@code v} (or {@code V}) is ignored, so {@code v0.5.0} compares equal to
 * {@code 0.5.0}.</li>
 * <li>The {@code major.minor.patch} core is compared segment-by-segment, numerically when
 * both segments parse as integers (so {@code 1.10.0} outranks {@code 1.2.0}), otherwise
 * lexicographically. A missing trailing segment is treated as {@code 0} ({@code 1.2} ==
 * {@code 1.2.0}).</li>
 * <li>A normal release outranks an otherwise-equal pre-release, so {@code 5.3.0} beats
 * {@code 5.3.0-rc1} (Helm picks the latest stable). Two pre-releases with the same core
 * are ordered lexicographically ({@code rc2} beats {@code rc1}).</li>
 * </ul>
 *
 * <p>
 * This is intentionally more forgiving than strict SemVer parsing: it never throws on a
 * malformed version, which makes it safe to sort the mixed bag of tags found in an
 * {@code index.yaml}. Consumers building an "upgrade available?" indicator can call
 * {@link #compare(String, String)} directly rather than reimplementing this logic.
 */
public final class ChartVersions {

	/**
	 * A {@link Comparator} over version strings using {@link #compare(String, String)}
	 * semantics (ascending: oldest first, newest last).
	 */
	public static final Comparator<String> COMPARATOR = ChartVersions::compare;

	private ChartVersions() {
	}

	/**
	 * Compares two chart version strings by lenient SemVer precedence.
	 * @param v1 the first version (may be {@code null})
	 * @param v2 the second version (may be {@code null})
	 * @return a negative integer, zero, or a positive integer as {@code v1} is older
	 * than, equal to, or newer than {@code v2}; {@code null} sorts before any
	 * non-{@code null} version
	 */
	public static int compare(String v1, String v2) {
		if (v1 == null && v2 == null) {
			return 0;
		}
		if (v1 == null) {
			return -1;
		}
		if (v2 == null) {
			return 1;
		}
		String a = stripLeadingV(v1);
		String b = stripLeadingV(v2);
		String core1 = a;
		String pre1 = "";
		int dash1 = a.indexOf('-');
		if (dash1 >= 0) {
			core1 = a.substring(0, dash1);
			pre1 = a.substring(dash1 + 1);
		}
		String core2 = b;
		String pre2 = "";
		int dash2 = b.indexOf('-');
		if (dash2 >= 0) {
			core2 = b.substring(0, dash2);
			pre2 = b.substring(dash2 + 1);
		}
		int coreCmp = compareCoreVersions(core1, core2);
		if (coreCmp != 0) {
			return coreCmp;
		}
		// Same major.minor.patch: per SemVer a normal version outranks a pre-release.
		boolean hasPre1 = !pre1.isEmpty();
		boolean hasPre2 = !pre2.isEmpty();
		if (hasPre1 != hasPre2) {
			return hasPre1 ? -1 : 1;
		}
		return pre1.compareTo(pre2);
	}

	private static int compareCoreVersions(String core1, String core2) {
		String[] p1 = core1.split("\\.");
		String[] p2 = core2.split("\\.");
		int n = Math.max(p1.length, p2.length);
		for (int i = 0; i < n; i++) {
			String a = (i < p1.length) ? p1[i] : "0";
			String b = (i < p2.length) ? p2[i] : "0";
			int ai = parseIntSafe(a);
			int bi = parseIntSafe(b);
			if (ai != Integer.MIN_VALUE && bi != Integer.MIN_VALUE) {
				if (ai != bi) {
					return Integer.compare(ai, bi);
				}
			}
			else {
				int c = a.compareTo(b);
				if (c != 0) {
					return c;
				}
			}
		}
		return 0;
	}

	private static String stripLeadingV(String v) {
		return (!v.isEmpty() && (v.charAt(0) == 'v' || v.charAt(0) == 'V')) ? v.substring(1) : v;
	}

	private static int parseIntSafe(String s) {
		try {
			return Integer.parseInt(s);
		}
		catch (NumberFormatException ex) {
			return Integer.MIN_VALUE;
		}
	}

}
