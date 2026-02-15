package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.Function;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Adler32;

/**
 * Encoding and hashing functions from Sprig library.
 * Includes Base64, Base32, and cryptographic hash functions.
 *
 * @see <a href="https://masterminds.github.io/sprig/encoding.html">Sprig Encoding Functions</a>
 */
public class EncodingFunctions {

    public static Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();

        // Base64 encoding/decoding
        functions.put("b64enc", b64enc());
        functions.put("b64dec", b64dec());

        // Base32 encoding/decoding
        functions.put("b32enc", b32enc());
        functions.put("b32dec", b32dec());

        // Cryptographic hash functions
        functions.put("sha1sum", sha1sum());
        functions.put("sha256sum", sha256sum());
        functions.put("sha512sum", sha512sum());

        // Checksum functions
        functions.put("adler32sum", adler32sum());

        return functions;
    }

    // ========== Base64 Functions ==========

    /**
     * Encodes a string to Base64.
     */
    private static Function b64enc() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            String input = String.valueOf(args[0]);
            return Base64.getEncoder().encodeToString(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        };
    }

    /**
     * Decodes a Base64 string.
     */
    private static Function b64dec() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            try {
                String input = String.valueOf(args[0]);
                byte[] decoded = Base64.getDecoder().decode(input);
                return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return "";
            }
        };
    }

    // ========== Base32 Functions ==========

    /**
     * Encodes a string to Base32.
     * Note: Java doesn't have built-in Base32, this is a simplified implementation.
     */
    private static Function b32enc() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            String input = String.valueOf(args[0]);
            return encodeBase32(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        };
    }

    /**
     * Decodes a Base32 string.
     * Note: Java doesn't have built-in Base32, this is a simplified implementation.
     */
    private static Function b32dec() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            try {
                String input = String.valueOf(args[0]);
                byte[] decoded = decodeBase32(input);
                return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        };
    }

    // ========== Cryptographic Hash Functions ==========

    /**
     * Returns SHA-1 hash of the input string as hexadecimal.
     */
    private static Function sha1sum() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] hash = md.digest(String.valueOf(args[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return bytesToHex(hash);
            } catch (Exception e) {
                return "";
            }
        };
    }

    /**
     * Returns SHA-256 hash of the input string as hexadecimal.
     */
    private static Function sha256sum() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(String.valueOf(args[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return bytesToHex(hash);
            } catch (Exception e) {
                return "";
            }
        };
    }

    /**
     * Returns SHA-512 hash of the input string as hexadecimal.
     */
    private static Function sha512sum() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-512");
                byte[] hash = md.digest(String.valueOf(args[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return bytesToHex(hash);
            } catch (Exception e) {
                return "";
            }
        };
    }

    // ========== Checksum Functions ==========

    /**
     * Returns Adler-32 checksum of the input string.
     */
    private static Function adler32sum() {
        return args -> {
            if (args.length == 0 || args[0] == null) return 0L;
            Adler32 adler = new Adler32();
            adler.update(String.valueOf(args[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return adler.getValue();
        };
    }

    // ========== Helper Methods ==========

    /**
     * Converts byte array to hexadecimal string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }

    /**
     * Simple Base32 encoding implementation (RFC 4648).
     */
    private static String encodeBase32(byte[] input) {
        final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : input) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }

        if (bitsLeft > 0) {
            result.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }

        // Add padding
        while (result.length() % 8 != 0) {
            result.append('=');
        }

        return result.toString();
    }

    /**
     * Simple Base32 decoding implementation (RFC 4648).
     */
    private static byte[] decodeBase32(String input) {
        final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        input = input.replaceAll("=", "");

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;

        for (char c : input.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(Character.toUpperCase(c));
            if (val == -1) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }

        return out.toByteArray();
    }
}
