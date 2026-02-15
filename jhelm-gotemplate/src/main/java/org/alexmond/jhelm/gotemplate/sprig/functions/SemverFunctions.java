package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.Function;

import java.util.HashMap;
import java.util.Map;

/**
 * Semantic versioning functions from Sprig library.
 * Includes version parsing and comparison operations.
 *
 * @see <a href="https://masterminds.github.io/sprig/semver.html">Sprig Semver Functions</a>
 */
public class SemverFunctions {

    public static Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();

        // Semantic version parsing and comparison
        functions.put("semver", semver());
        functions.put("semverCompare", semverCompare());

        return functions;
    }

    // ========== Semantic Version Functions ==========

    /**
     * Parses a semantic version string and returns a Map with version components.
     *
     * @return Map with keys: Major, Minor, Patch, Prerelease, Metadata, Original
     */
    private static Function semver() {
        return args -> {
            if (args.length == 0 || args[0] == null) return null;

            String version = String.valueOf(args[0]);
            // Remove leading 'v' or 'V' if present
            if (version.toLowerCase().startsWith("v")) {
                version = version.substring(1);
            }

            Map<String, Object> semver = new HashMap<>();
            semver.put("Original", String.valueOf(args[0]));

            try {
                // Split by '+' to separate build metadata
                String[] metadataParts = version.split("\\+", 2);
                String versionCore = metadataParts[0];
                String metadata = metadataParts.length > 1 ? metadataParts[1] : "";

                // Split by '-' to separate prerelease
                String[] prereleaseParts = versionCore.split("-", 2);
                String versionNumbers = prereleaseParts[0];
                String prerelease = prereleaseParts.length > 1 ? prereleaseParts[1] : "";

                // Parse version numbers
                String[] parts = versionNumbers.split("\\.");
                semver.put("Major", parts.length > 0 ? parseLong(parts[0]) : 0L);
                semver.put("Minor", parts.length > 1 ? parseLong(parts[1]) : 0L);
                semver.put("Patch", parts.length > 2 ? parseLong(parts[2]) : 0L);
                semver.put("Prerelease", prerelease);
                semver.put("Metadata", metadata);

            } catch (Exception e) {
                // On error, return default values
                semver.put("Major", 0L);
                semver.put("Minor", 0L);
                semver.put("Patch", 0L);
                semver.put("Prerelease", "");
                semver.put("Metadata", "");
            }

            return semver;
        };
    }

    /**
     * Compares a version against a constraint.
     * <p>
     * Supports operators: =, !=, >, <, >=, <=, ~, ^, ||, -
     * <p>
     * Examples:
     * - "=1.2.3" - exact match
     * - ">1.2.3" - greater than
     * - ">=1.2.3 <2.0.0" - range
     * - "~1.2.3" - patch-level changes (>=1.2.3 <1.3.0)
     * - "^1.2.3" - minor-level changes (>=1.2.3 <2.0.0)
     *
     * @return {@code true} if version satisfies constraint
     */
    private static Function semverCompare() {
        return args -> {
            if (args.length < 2) return false;

            String constraint = String.valueOf(args[0]).trim();
            String versionStr = String.valueOf(args[1]).trim();

            // Parse the version
            SemanticVersion version = parseVersion(versionStr);
            if (version == null) return false;

            // Handle OR operator (||)
            if (constraint.contains("||")) {
                String[] orParts = constraint.split("\\|\\|");
                for (String part : orParts) {
                    if (matchesConstraint(version, part.trim())) {
                        return true;
                    }
                }
                return false;
            }

            // Handle range (e.g., ">=1.2.3 <2.0.0")
            if (constraint.contains(" ")) {
                String[] rangeParts = constraint.split("\\s+");
                for (String part : rangeParts) {
                    if (!matchesConstraint(version, part.trim())) {
                        return false;
                    }
                }
                return true;
            }

            // Single constraint
            return matchesConstraint(version, constraint);
        };
    }

    // ========== Helper Classes and Methods ==========

    private static SemanticVersion parseVersion(String version) {
        if (version == null || version.isEmpty()) return null;

        // Remove leading 'v' or 'V'
        if (version.toLowerCase().startsWith("v")) {
            version = version.substring(1);
        }

        try {
            // Remove build metadata
            String versionCore = version.split("\\+")[0];

            // Split prerelease
            String[] prereleaseParts = versionCore.split("-", 2);
            String versionNumbers = prereleaseParts[0];
            String prerelease = prereleaseParts.length > 1 ? prereleaseParts[1] : "";

            // Parse version numbers
            String[] parts = versionNumbers.split("\\.");
            long major = parts.length > 0 ? parseLong(parts[0]) : 0;
            long minor = parts.length > 1 ? parseLong(parts[1]) : 0;
            long patch = parts.length > 2 ? parseLong(parts[2]) : 0;

            return new SemanticVersion(major, minor, patch, prerelease);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean matchesConstraint(SemanticVersion version, String constraint) {
        constraint = constraint.trim();

        // Tilde operator: ~1.2.3 means >=1.2.3 <1.3.0
        if (constraint.startsWith("~")) {
            SemanticVersion base = parseVersion(constraint.substring(1));
            if (base == null) return false;
            return version.major == base.major &&
                    version.minor == base.minor &&
                    version.patch >= base.patch;
        }

        // Caret operator: ^1.2.3 means >=1.2.3 <2.0.0
        if (constraint.startsWith("^")) {
            SemanticVersion base = parseVersion(constraint.substring(1));
            if (base == null) return false;
            if (base.major > 0) {
                return version.major == base.major && version.compareTo(base) >= 0;
            } else if (base.minor > 0) {
                return version.major == 0 && version.minor == base.minor && version.compareTo(base) >= 0;
            } else {
                return version.compareTo(base) == 0;
            }
        }

        // Comparison operators
        String operator;
        String versionPart;

        if (constraint.startsWith(">=")) {
            operator = ">=";
            versionPart = constraint.substring(2).trim();
        } else if (constraint.startsWith("<=")) {
            operator = "<=";
            versionPart = constraint.substring(2).trim();
        } else if (constraint.startsWith("!=")) {
            operator = "!=";
            versionPart = constraint.substring(2).trim();
        } else if (constraint.startsWith(">")) {
            operator = ">";
            versionPart = constraint.substring(1).trim();
        } else if (constraint.startsWith("<")) {
            operator = "<";
            versionPart = constraint.substring(1).trim();
        } else if (constraint.startsWith("=")) {
            operator = "=";
            versionPart = constraint.substring(1).trim();
        } else {
            // No operator, assume exact match
            operator = "=";
            versionPart = constraint;
        }

        SemanticVersion constraintVersion = parseVersion(versionPart);
        if (constraintVersion == null) return false;

        int comparison = version.compareTo(constraintVersion);

        return switch (operator) {
            case "=" -> comparison == 0;
            case "!=" -> comparison != 0;
            case ">" -> comparison > 0;
            case "<" -> comparison < 0;
            case ">=" -> comparison >= 0;
            case "<=" -> comparison <= 0;
            default -> false;
        };
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static class SemanticVersion {
        long major;
        long minor;
        long patch;
        String prerelease;

        SemanticVersion(long major, long minor, long patch, String prerelease) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.prerelease = prerelease != null ? prerelease : "";
        }

        int compareTo(SemanticVersion other) {
            if (major != other.major) return Long.compare(major, other.major);
            if (minor != other.minor) return Long.compare(minor, other.minor);
            if (patch != other.patch) return Long.compare(patch, other.patch);

            // Prerelease comparison: versions with prerelease < versions without
            if (prerelease.isEmpty() && !other.prerelease.isEmpty()) return 1;
            if (!prerelease.isEmpty() && other.prerelease.isEmpty()) return -1;
            return prerelease.compareTo(other.prerelease);
        }
    }
}
