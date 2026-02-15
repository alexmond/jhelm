package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.Function;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Cryptographic and random generation functions from Sprig library.
 * Includes password generation, random string generation, and certificate generation.
 * <p>
 * Note: Certificate generation functions are simplified stubs.
 * Production implementation would require proper cryptography libraries.
 *
 * @see <a href="https://masterminds.github.io/sprig/crypto.html">Sprig Crypto Functions</a>
 */
public class CryptoFunctions {

    public static Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();

        // Random string generation
        functions.put("randAlphaNum", randAlphaNum());
        functions.put("randAlpha", randAlpha());
        functions.put("randNumeric", randNumeric());
        functions.put("randAscii", randAscii());

        // Password functions
        functions.put("htpasswd", htpasswd());
        functions.put("derivePassword", derivePassword());

        // Private key generation (stub)
        functions.put("genPrivateKey", genPrivateKey());

        // Certificate generation (stubs)
        functions.put("genCA", genCA());
        functions.put("genSignedCert", genSignedCert());
        functions.put("genSelfSignedCert", genSelfSignedCert());

        return functions;
    }

    // ========== Random String Generation Functions ==========

    /**
     * Generates a random alphanumeric string of given length.
     * Uses characters A-Z, a-z, 0-9.
     */
    private static Function randAlphaNum() {
        return args -> {
            if (args.length == 0) return "";
            int length = ((Number) args[0]).intValue();
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            Random random = new Random();
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            return sb.toString();
        };
    }

    /**
     * Generates a random alphabetic string of given length.
     * Uses characters A-Z, a-z.
     */
    private static Function randAlpha() {
        return args -> {
            if (args.length == 0) return "";
            int length = ((Number) args[0]).intValue();
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
            Random random = new Random();
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            return sb.toString();
        };
    }

    /**
     * Generates a random numeric string of given length.
     * Uses digits 0-9.
     */
    private static Function randNumeric() {
        return args -> {
            if (args.length == 0) return "";
            int length = ((Number) args[0]).intValue();
            String chars = "0123456789";
            Random random = new Random();
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            return sb.toString();
        };
    }

    /**
     * Generates a random ASCII string of given length.
     * Uses printable ASCII characters (33-126).
     */
    private static Function randAscii() {
        return args -> {
            if (args.length == 0) return "";
            int length = ((Number) args[0]).intValue();
            Random random = new Random();
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                // ASCII printable characters: 33-126
                sb.append((char) (random.nextInt(94) + 33));
            }
            return sb.toString();
        };
    }

    // ========== Password Functions ==========

    /**
     * Generates an htpasswd entry for HTTP Basic Authentication.
     * <p>
     * Simplified implementation - returns basic bcrypt-like format.
     * Production implementation should use proper BCrypt or Apache Commons Codec.
     *
     * @return htpasswd entry in format "username:$2y$hash"
     */
    private static Function htpasswd() {
        return args -> {
            if (args.length < 2) return "";
            String username = String.valueOf(args[0]);
            String password = String.valueOf(args[1]);
            // Return a basic bcrypt-like format (not real htpasswd, just placeholder)
            // In production, use BCrypt or Apache Commons Codec
            return username + ":$2y$" + password.hashCode();
        };
    }

    /**
     * Derives a password based on input parameters.
     * <p>
     * Simplified stub implementation.
     * Production implementation should use proper key derivation function (PBKDF2, bcrypt, etc.).
     *
     * @return derived password
     */
    private static Function derivePassword() {
        return args -> {
            if (args.length < 4) return "";
            // Simplified implementation - in production use PBKDF2 or similar
            long counter = args[0] instanceof Number ? ((Number) args[0]).longValue() : 1;
            String context = String.valueOf(args[1]);
            String masterPassword = String.valueOf(args[2]);
            String user = String.valueOf(args[3]);

            // Simple hash-based derivation (not cryptographically secure)
            String combined = counter + context + masterPassword + user;
            int hash = combined.hashCode();
            return "derived_" + Math.abs(hash);
        };
    }

    // ========== Key Generation Functions ==========

    /**
     * Generates a private key.
     * <p>
     * Simplified stub implementation.
     * Production implementation would use Java Cryptography Architecture (JCA).
     *
     * @return PEM-encoded private key (placeholder)
     */
    private static Function genPrivateKey() {
        return args -> {
            // Simplified implementation - returns a placeholder private key
            // In production, this would use proper cryptography libraries (JCA)
            String algorithm = args.length > 0 ? String.valueOf(args[0]).toLowerCase() : "rsa";
            return "-----BEGIN " + algorithm.toUpperCase() + " PRIVATE KEY-----\n" +
                    "MIIE...PLACEHOLDER...==\n" +
                    "-----END " + algorithm.toUpperCase() + " PRIVATE KEY-----";
        };
    }

    // ========== Certificate Generation Functions ==========

    /**
     * Generates a Certificate Authority (CA) certificate.
     * <p>
     * Simplified stub implementation.
     * Production implementation would use Bouncy Castle or similar library.
     *
     * @return Map with "Cert" and "Key" fields containing PEM-encoded certificate and key (placeholders)
     */
    private static Function genCA() {
        return args -> {
            // Simplified implementation - returns a placeholder certificate structure
            // In production, this would use proper cryptography libraries
            Map<String, Object> ca = new HashMap<>();
            ca.put("Cert", """
                    -----BEGIN CERTIFICATE-----
                    MIIC...CA_PLACEHOLDER...==
                    -----END CERTIFICATE-----""");
            ca.put("Key", """
                    -----BEGIN RSA PRIVATE KEY-----
                    MIIE...CA_KEY_PLACEHOLDER...==
                    -----END RSA PRIVATE KEY-----""");
            return ca;
        };
    }

    /**
     * Generates a signed certificate using a CA.
     * <p>
     * Simplified stub implementation.
     * Production implementation would use Bouncy Castle or similar library.
     *
     * @return Map with "Cert" and "Key" fields containing PEM-encoded certificate and key (placeholders)
     */
    private static Function genSignedCert() {
        return args -> {
            // Simplified implementation - returns a placeholder certificate structure
            // In production, this would generate a proper signed certificate
            // Args: cn, ips, alternateIPs, daysValid, ca
            Map<String, Object> cert = new HashMap<>();
            cert.put("Cert", """
                    -----BEGIN CERTIFICATE-----
                    MIIC...SIGNED_PLACEHOLDER...==
                    -----END CERTIFICATE-----""");
            cert.put("Key", """
                    -----BEGIN RSA PRIVATE KEY-----
                    MIIE...SIGNED_KEY_PLACEHOLDER...==
                    -----END RSA PRIVATE KEY-----""");
            return cert;
        };
    }

    /**
     * Generates a self-signed certificate.
     * <p>
     * Simplified stub implementation.
     * Production implementation would use Bouncy Castle or similar library.
     *
     * @return Map with "Cert" and "Key" fields containing PEM-encoded certificate and key (placeholders)
     */
    private static Function genSelfSignedCert() {
        return args -> {
            // Simplified implementation - returns a placeholder certificate structure
            // In production, this would generate a proper self-signed certificate
            Map<String, Object> cert = new HashMap<>();
            cert.put("Cert", """
                    -----BEGIN CERTIFICATE-----
                    MIIC...SELF_SIGNED_PLACEHOLDER...==
                    -----END CERTIFICATE-----""");
            cert.put("Key", """
                    -----BEGIN RSA PRIVATE KEY-----
                    MIIE...SELF_SIGNED_KEY_PLACEHOLDER...==
                    -----END RSA PRIVATE KEY-----""");
            return cert;
        };
    }
}
