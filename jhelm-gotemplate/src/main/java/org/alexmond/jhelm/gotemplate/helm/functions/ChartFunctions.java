package org.alexmond.jhelm.gotemplate.helm.functions;

import org.alexmond.jhelm.gotemplate.Function;

import java.util.HashMap;
import java.util.Map;

/**
 * Helm chart-specific helper functions
 * Based on: <a href="https://helm.sh/docs/chart_template_guide/function_list/">https://helm.sh/docs/chart_template_guide/function_list/</a>
 */
public class ChartFunctions {

    public static Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();

        // Semantic version comparison
        functions.put("semverCompare", semverCompare());
        functions.put("semver", semver());

        // Certificate generation stubs (for Bitnami charts compatibility)
        functions.put("buildCustomCert", buildCustomCert());
        functions.put("genCA", genCA());
        functions.put("genSelfSignedCert", genSelfSignedCert());
        functions.put("genSignedCert", genSignedCert());
        functions.put("genPrivateKey", genPrivateKey());

        return functions;
    }

    /**
     * semverCompare compares semantic versions
     * Syntax: semverCompare ">=1.2.3" "1.3.0"
     * Returns {@code true} if comparison matches
     */
    private static Function semverCompare() {
        return args -> {
            // Simplified implementation - always returns true for compatibility
            // TODO: Implement proper semantic version comparison
            // using org.semver4j or similar library
            return true;
        };
    }

    /**
     * semver parses a semantic version string
     * Returns a map with Major, Minor, Patch fields
     */
    private static Function semver() {
        return args -> {
            if (args.length == 0) return null;
            String version = String.valueOf(args[0]);

            // Remove leading 'v' if present
            if (version.startsWith("v") || version.startsWith("V")) {
                version = version.substring(1);
            }

            Map<String, Object> semver = new HashMap<>();
            String[] parts = version.split("\\.");

            try {
                semver.put("Major", parts.length > 0 ? Long.parseLong(parts[0]) : 0L);
                semver.put("Minor", parts.length > 1 ? Long.parseLong(parts[1]) : 0L);

                // Handle patch version with pre-release info (e.g., "3-alpha.1")
                if (parts.length > 2) {
                    String patch = parts[2].split("-")[0];
                    semver.put("Patch", Long.parseLong(patch));
                } else {
                    semver.put("Patch", 0L);
                }
            } catch (NumberFormatException e) {
                semver.put("Major", 0L);
                semver.put("Minor", 0L);
                semver.put("Patch", 0L);
            }

            return semver;
        };
    }

    /**
     * buildCustomCert generates a custom TLS certificate
     * Stub implementation for Bitnami charts compatibility
     * Returns a map with Cert and Key fields
     */
    private static Function buildCustomCert() {
        return args -> {
            Map<String, String> cert = new HashMap<>();
            cert.put("Cert", "-----BEGIN CERTIFICATE-----\n...stub...\n-----END CERTIFICATE-----");
            cert.put("Key", "-----BEGIN PRIVATE KEY-----\n...stub...\n-----END PRIVATE KEY-----");
            return cert;
        };
    }

    /**
     * genCA generates a new Certificate Authority
     * Stub implementation - returns placeholder certificate
     */
    private static Function genCA() {
        return args -> {
            Map<String, String> ca = new HashMap<>();
            ca.put("Cert", "-----BEGIN CERTIFICATE-----\nMIIC...CA_STUB...==\n-----END CERTIFICATE-----");
            ca.put("Key", "-----BEGIN RSA PRIVATE KEY-----\nMIIE...CA_KEY_STUB...==\n-----END RSA PRIVATE KEY-----");
            return ca;
        };
    }

    /**
     * genSelfSignedCert generates a self-signed certificate
     * Stub implementation
     */
    private static Function genSelfSignedCert() {
        return args -> {
            Map<String, String> cert = new HashMap<>();
            cert.put("Cert", "-----BEGIN CERTIFICATE-----\nMIIC...SELF_SIGNED_STUB...==\n-----END CERTIFICATE-----");
            cert.put("Key", "-----BEGIN RSA PRIVATE KEY-----\nMIIE...SELF_SIGNED_KEY_STUB...==\n-----END RSA PRIVATE KEY-----");
            return cert;
        };
    }

    /**
     * genSignedCert generates a certificate signed by a CA
     * Stub implementation
     * Args: cn, ips, alternateIPs, daysValid, ca
     */
    private static Function genSignedCert() {
        return args -> {
            Map<String, String> cert = new HashMap<>();
            cert.put("Cert", "-----BEGIN CERTIFICATE-----\nMIIC...SIGNED_STUB...==\n-----END CERTIFICATE-----");
            cert.put("Key", "-----BEGIN RSA PRIVATE KEY-----\nMIIE...SIGNED_KEY_STUB...==\n-----END RSA PRIVATE KEY-----");
            return cert;
        };
    }

    /**
     * genPrivateKey generates a new private key
     * Stub implementation
     */
    private static Function genPrivateKey() {
        return args -> {
            return "-----BEGIN RSA PRIVATE KEY-----\nMIIE...PRIVATE_KEY_STUB...==\n-----END RSA PRIVATE KEY-----";
        };
    }
}
