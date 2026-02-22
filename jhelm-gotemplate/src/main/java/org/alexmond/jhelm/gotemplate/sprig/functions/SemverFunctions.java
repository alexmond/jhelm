package org.alexmond.jhelm.gotemplate.sprig.functions;

import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.SemverException;
import org.alexmond.jhelm.gotemplate.Function;

import java.util.HashMap;
import java.util.Map;

/**
 * Semantic versioning functions from Sprig library.
 * Uses Semver4j for version parsing and constraint evaluation.
 *
 * @see <a href="https://masterminds.github.io/sprig/semver.html">Sprig Semver Functions</a>
 */
public class SemverFunctions {

    public static Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();
        functions.put("semver", semver());
        functions.put("semverCompare", semverCompare());
        return functions;
    }

    /**
     * Parses a semantic version string and returns a Map with version components.
     *
     * @return Map with keys: Major, Minor, Patch, Prerelease, Metadata, Original
     */
    private static Function semver() {
        return args -> {
            if (args.length == 0 || args[0] == null) return null;
            String versionStr = String.valueOf(args[0]);
            Map<String, Object> result = new HashMap<>();
            result.put("Original", versionStr);
            try {
                Semver v = new Semver(normalizeVersion(versionStr), Semver.SemverType.LOOSE);
                result.put("Major", v.getMajor() != null ? v.getMajor().longValue() : 0L);
                result.put("Minor", v.getMinor() != null ? v.getMinor().longValue() : 0L);
                result.put("Patch", v.getPatch() != null ? v.getPatch().longValue() : 0L);
                result.put("Prerelease", v.getSuffixTokens().length > 0
                        ? String.join(".", v.getSuffixTokens()) : "");
                result.put("Metadata", v.getBuild() != null ? String.join(".", v.getBuild()) : "");
            } catch (SemverException e) {
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
     * Compares a version against a constraint string.
     * Supports operators: =, !=, >, <, >=, <=, ~, ^, ||, and space-separated AND ranges.
     *
     * @return {@code true} if the version satisfies the constraint
     */
    private static Function semverCompare() {
        return args -> {
            if (args.length < 2) return false;
            String constraint = String.valueOf(args[0]).trim();
            String versionStr = String.valueOf(args[1]).trim();
            try {
                Semver v = new Semver(normalizeVersion(versionStr), Semver.SemverType.LOOSE);
                return evaluateConstraint(v, constraint);
            } catch (Exception e) {
                return false;
            }
        };
    }

    /**
     * Evaluates a constraint expression against a parsed version.
     * Handles OR (||) and AND (space-separated) compound constraints.
     */
    private static boolean evaluateConstraint(Semver version, String constraint) {
        // OR: split on ||
        if (constraint.contains("||")) {
            for (String part : constraint.split("\\|\\|")) {
                if (evaluateConstraint(version, part.trim())) {
                    return true;
                }
            }
            return false;
        }
        // AND: split on whitespace (e.g. ">=1.0.0 <2.0.0")
        String[] parts = constraint.trim().split("\\s+");
        if (parts.length > 1) {
            for (String part : parts) {
                if (!evaluateSingleConstraint(version, part.trim())) {
                    return false;
                }
            }
            return true;
        }
        return evaluateSingleConstraint(version, constraint.trim());
    }

    /**
     * Evaluates a single constraint token (e.g. ">=1.2.3", "~1.2.3", "^1.2.3").
     */
    private static boolean evaluateSingleConstraint(Semver version, String constraint) {
        if (constraint.isEmpty()) return true;

        // Tilde: ~1.2.3 → >=1.2.3 <1.3.0
        if (constraint.startsWith("~")) {
            String base = constraint.substring(1).trim();
            Semver baseV = parseLeniently(base);
            if (baseV == null) return false;
            Semver upper = new Semver(baseV.getMajor() + "." + (baseV.getMinor() + 1) + ".0",
                    Semver.SemverType.LOOSE);
            return version.isGreaterThanOrEqualTo(baseV) && version.isLowerThan(upper);
        }

        // Caret: ^1.2.3 → >=1.2.3 <2.0.0  (^0.2.3 → >=0.2.3 <0.3.0, etc.)
        if (constraint.startsWith("^")) {
            String base = constraint.substring(1).trim();
            Semver baseV = parseLeniently(base);
            if (baseV == null) return false;
            long major = baseV.getMajor() != null ? baseV.getMajor() : 0;
            long minor = baseV.getMinor() != null ? baseV.getMinor() : 0;
            Semver upper;
            if (major > 0) {
                upper = new Semver((major + 1) + ".0.0", Semver.SemverType.LOOSE);
            } else if (minor > 0) {
                upper = new Semver("0." + (minor + 1) + ".0", Semver.SemverType.LOOSE);
            } else {
                // ^0.0.x → exact match
                return version.isEquivalentTo(baseV);
            }
            return version.isGreaterThanOrEqualTo(baseV) && version.isLowerThan(upper);
        }

        // Standard operators: >=, <=, !=, >, <, =
        if (constraint.startsWith(">=")) {
            Semver c = parseLeniently(constraint.substring(2).trim());
            return c != null && version.isGreaterThanOrEqualTo(c);
        }
        if (constraint.startsWith("<=")) {
            Semver c = parseLeniently(constraint.substring(2).trim());
            return c != null && version.isLowerThanOrEqualTo(c);
        }
        if (constraint.startsWith("!=")) {
            Semver c = parseLeniently(constraint.substring(2).trim());
            return c != null && !version.isEquivalentTo(c);
        }
        if (constraint.startsWith(">")) {
            Semver c = parseLeniently(constraint.substring(1).trim());
            return c != null && version.isGreaterThan(c);
        }
        if (constraint.startsWith("<")) {
            Semver c = parseLeniently(constraint.substring(1).trim());
            return c != null && version.isLowerThan(c);
        }
        if (constraint.startsWith("=")) {
            Semver c = parseLeniently(constraint.substring(1).trim());
            return c != null && version.isEquivalentTo(c);
        }

        // No operator — exact match
        Semver c = parseLeniently(constraint);
        return c != null && version.isEquivalentTo(c);
    }

    private static Semver parseLeniently(String version) {
        try {
            return new Semver(normalizeVersion(version), Semver.SemverType.LOOSE);
        } catch (Exception e) {
            return null;
        }
    }

    /** Strips a leading 'v' or 'V' prefix that Semver4j LOOSE mode doesn't accept. */
    private static String normalizeVersion(String version) {
        if (version != null && version.length() > 1
                && (version.charAt(0) == 'v' || version.charAt(0) == 'V')) {
            return version.substring(1);
        }
        return version;
    }
}
