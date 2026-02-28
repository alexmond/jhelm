package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.semver4j.Semver;

import org.alexmond.jhelm.gotemplate.Function;

/**
 * Semantic versioning functions from Sprig library. Uses Semver4j for version parsing and
 * constraint evaluation.
 *
 * @see <a href="https://masterminds.github.io/sprig/semver.html">Sprig Semver
 * Functions</a>
 */
public final class SemverFunctions {

	private SemverFunctions() {
	}

	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();
		functions.put("semver", semver());
		functions.put("semverCompare", semverCompare());
		return functions;
	}

	/**
	 * Parses a semantic version string and returns a Map with version components.
	 * @return Map with keys: Major, Minor, Patch, Prerelease, Metadata, Original
	 */
	private static Function semver() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return null;
			}
			String versionStr = String.valueOf(args[0]);
			Map<String, Object> result = new HashMap<>();
			result.put("Original", versionStr);
			Semver v = Semver.coerce(normalizeVersion(versionStr));
			if (v != null) {
				result.put("Major", (long) v.getMajor());
				result.put("Minor", (long) v.getMinor());
				result.put("Patch", (long) v.getPatch());
				result.put("Prerelease", !v.getPreRelease().isEmpty() ? String.join(".", v.getPreRelease()) : "");
				result.put("Metadata", !v.getBuild().isEmpty() ? String.join(".", v.getBuild()) : "");
			}
			else {
				result.put("Major", 0L);
				result.put("Minor", 0L);
				result.put("Patch", 0L);
				result.put("Prerelease", "");
				result.put("Metadata", "");
			}
			return result;
		};
	}

	/**
	 * Compares a version against a constraint string. Supports operators: =, !=, >,
	 * <, >=, <=, ~, ^, ||, and space-separated AND ranges.
	 * @return {@code true} if the version satisfies the constraint
	 */
	private static Function semverCompare() {
		return (args) -> {
			if (args.length < 2) {
				return false;
			}
			String constraint = String.valueOf(args[0]).trim();
			String versionStr = String.valueOf(args[1]).trim();
			try {
				Semver v = Semver.coerce(normalizeVersion(versionStr));
				if (v == null) {
					return false;
				}
				// Handle != operator (not supported by semver4j satisfies)
				if (constraint.startsWith("!=")) {
					String target = constraint.substring(2).trim();
					Semver c = Semver.coerce(normalizeVersion(target));
					return c != null && !v.isEquivalentTo(c);
				}
				return v.satisfies(normalizeConstraint(constraint), true);
			}
			catch (Exception ex) {
				return false;
			}
		};
	}

	/** Strips a leading 'v' or 'V' prefix. */
	private static String normalizeVersion(String version) {
		if (version != null && version.length() > 1 && (version.charAt(0) == 'v' || version.charAt(0) == 'V')) {
			return version.substring(1);
		}
		return version;
	}

	/**
	 * Normalizes incomplete version numbers in constraints. Helm's Go semver library
	 * accepts 2-part versions like "1.13-0" (meaning 1.13.0-0), but semver4j requires
	 * strict 3-part versions. This adds the missing ".0" patch component.
	 */
	private static final Pattern TWO_PART_VERSION = Pattern.compile("(?<!\\d\\.)(\\d+\\.\\d+)(-\\S+)?(?=\\s|$|\\|)");

	private static String normalizeConstraint(String constraint) {
		Matcher m = TWO_PART_VERSION.matcher(constraint);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String majorMinor = m.group(1);
			String suffix = (m.group(2) != null) ? m.group(2) : "";
			// Only add .0 if there's no third part already
			if (!majorMinor.matches(".*\\.\\d+\\.\\d+.*")) {
				m.appendReplacement(sb, majorMinor + ".0" + suffix);
			}
		}
		m.appendTail(sb);
		return sb.toString();
	}

}
