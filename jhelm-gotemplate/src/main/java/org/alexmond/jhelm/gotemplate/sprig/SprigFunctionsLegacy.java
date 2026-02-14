package org.alexmond.jhelm.gotemplate.sprig;

import org.alexmond.jhelm.gotemplate.Function;
import org.apache.hc.core5.net.URIBuilder;

import java.util.*;

/**
 * Legacy Sprig functions that haven't been refactored into category-specific classes yet.
 * <p>
 * Most functions have been moved to:
 * - StringFunctions.java (string operations)
 * - CollectionFunctions.java (list/dict operations)
 * - LogicFunctions.java (control flow)
 * - MathFunctions.java (arithmetic)
 * - EncodingFunctions.java (base64, hashing)
 * - CryptoFunctions.java (random, certificates)
 * - DateFunctions.java (date/time)
 * - ReflectionFunctions.java (type inspection)
 * - NetworkFunctions.java (DNS)
 * - SemverFunctions.java (version parsing)
 * <p>
 * Remaining functions in this class:
 * - URL parsing utilities
 * <p>
 * NOTE: This class is intentionally minimal. Most functions are duplicates from category classes
 * and are kept only for backwards compatibility during refactoring.
 *
 * @deprecated Use category-specific classes instead. This will be removed in a future version.
 */
@Deprecated
public class SprigFunctionsLegacy {

    public static Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();

        // URL parsing - unique function not yet in other categories
        functions.put("urlParse", urlParseFunc());

        // Backwards compatibility aliases for Helm functions
        // NOTE: These are actually Helm-specific functions, not Sprig.
        // They are kept here for backwards compatibility only.
        // New code should use HelmFunctions.getFunctions() to get these.
        functions.put("required", requiredCompat());
        functions.put("toJson", toJsonCompat());

        return functions;
    }

    /**
     * URL parsing function - parses URL into components
     * Returns map with: scheme, host, port, path, query
     */
    private static Function urlParseFunc() {
        return args -> {
            if (args.length == 0) return Map.of();
            try {
                URIBuilder uriBuilder = new URIBuilder(String.valueOf(args[0]));
                Map<String, Object> result = new HashMap<>();
                result.put("scheme", uriBuilder.getScheme() != null ? uriBuilder.getScheme() : "");
                result.put("host", uriBuilder.getHost() != null ? uriBuilder.getHost() : "");
                result.put("port", uriBuilder.getPort() > 0 ? String.valueOf(uriBuilder.getPort()) : "");
                result.put("path", uriBuilder.getPath() != null ? uriBuilder.getPath() : "");

                // Get query string
                StringBuilder queryString = new StringBuilder();
                if (uriBuilder.getQueryParams() != null && !uriBuilder.getQueryParams().isEmpty()) {
                    for (int i = 0; i < uriBuilder.getQueryParams().size(); i++) {
                        if (i > 0) queryString.append("&");
                        var param = uriBuilder.getQueryParams().get(i);
                        queryString.append(param.getName());
                        if (param.getValue() != null) {
                            queryString.append("=").append(param.getValue());
                        }
                    }
                }
                result.put("query", queryString.toString());
                return result;
            } catch (Exception e) {
                return Map.of();
            }
        };
    }

    /**
     * Backwards compatibility: required function
     * NOTE: This is actually a Helm template function, kept for backwards compatibility.
     *
     * @deprecated Use HelmFunctions (TemplateFunctions.required()) instead
     */
    @Deprecated
    private static Function requiredCompat() {
        return args -> {
            if (args.length < 2) throw new RuntimeException("required: insufficient arguments");
            String message = String.valueOf(args[0]);
            Object value = args[1];
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                throw new RuntimeException(message);
            }
            return value;
        };
    }

    /**
     * Backwards compatibility: toJson function
     * NOTE: This is actually a Helm conversion function, kept for backwards compatibility.
     * <p>
     * Simplified JSON serialization - for production use, see ConversionFunctions.toJson()
     *
     * @deprecated Use HelmFunctions (ConversionFunctions.toJson()) instead
     */
    @Deprecated
    private static Function toJsonCompat() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "null";
            Object obj = args[0];
            if (obj instanceof String) return "\"" + obj + "\"";
            if (obj instanceof Number || obj instanceof Boolean) return String.valueOf(obj);
            if (obj instanceof Map || obj instanceof Collection) {
                // Simplified - real implementation in ConversionFunctions uses Jackson
                return String.valueOf(obj);
            }
            return "\"" + obj + "\"";
        };
    }
}
