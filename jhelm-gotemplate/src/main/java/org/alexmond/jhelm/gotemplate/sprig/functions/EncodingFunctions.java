package org.alexmond.jhelm.gotemplate.sprig.functions;

import org.alexmond.jhelm.gotemplate.Function;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;

import java.nio.charset.StandardCharsets;
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

    private static final Base32 BASE32 = new Base32();

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

    private static Function b64enc() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            String input = String.valueOf(args[0]);
            return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
        };
    }

    private static Function b64dec() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            try {
                String input = String.valueOf(args[0]);
                byte[] decoded = Base64.getDecoder().decode(input);
                return new String(decoded, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return "";
            }
        };
    }

    private static Function b32enc() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            String input = String.valueOf(args[0]);
            return BASE32.encodeAsString(input.getBytes(StandardCharsets.UTF_8));
        };
    }

    private static Function b32dec() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            try {
                String input = String.valueOf(args[0]);
                if (!BASE32.isInAlphabet(input.replaceAll("=", "").toUpperCase())) return "";
                byte[] decoded = BASE32.decode(input);
                return new String(decoded, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        };
    }

    private static Function sha1sum() {
        return args -> hashWith("SHA-1", args);
    }

    private static Function sha256sum() {
        return args -> hashWith("SHA-256", args);
    }

    private static Function sha512sum() {
        return args -> hashWith("SHA-512", args);
    }

    private static String hashWith(String algorithm, Object[] args) {
        if (args.length == 0 || args[0] == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] hash = md.digest(String.valueOf(args[0]).getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(hash);
        } catch (Exception e) {
            return "";
        }
    }

    private static Function adler32sum() {
        return args -> {
            if (args.length == 0 || args[0] == null) return 0L;
            Adler32 adler = new Adler32();
            adler.update(String.valueOf(args[0]).getBytes(StandardCharsets.UTF_8));
            return adler.getValue();
        };
    }
}
