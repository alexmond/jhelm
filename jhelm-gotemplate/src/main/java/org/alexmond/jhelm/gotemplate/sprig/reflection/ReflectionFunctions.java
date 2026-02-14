package org.alexmond.jhelm.gotemplate.sprig.reflection;

import org.alexmond.jhelm.gotemplate.Function;

import java.util.*;

/**
 * Type reflection and introspection functions from Sprig library.
 * Includes type checking, kind checking, and deep equality comparison.
 *
 * @see <a href="https://masterminds.github.io/sprig/reflection.html">Sprig Reflection Functions</a>
 */
public class ReflectionFunctions {

    public static Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();

        // Type information
        functions.put("typeOf", typeOf());
        functions.put("kindOf", kindOf());

        // Type checking
        functions.put("typeIs", typeIs());
        functions.put("typeIsLike", typeIsLike());
        functions.put("kindIs", kindIs());

        // Equality
        functions.put("deepEqual", deepEqual());

        return functions;
    }

    // ========== Type Information Functions ==========

    /**
     * Returns the full Java type name of the value.
     * Returns "nil" for null values.
     */
    private static Function typeOf() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "nil";
            return args[0].getClass().getName();
        };
    }

    /**
     * Returns the kind (basic type category) of the value.
     * Kinds: string, number, bool, map, slice, struct, invalid
     */
    private static Function kindOf() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "invalid";
            Class<?> c = args[0].getClass();

            if (c == String.class) return "string";
            if (Number.class.isAssignableFrom(c)) return "number";
            if (Boolean.class.isAssignableFrom(c)) return "bool";
            if (Map.class.isAssignableFrom(c)) return "map";
            if (Collection.class.isAssignableFrom(c) || c.isArray()) return "slice";
            return "struct";
        };
    }

    // ========== Type Checking Functions ==========

    /**
     * Checks if the value is of a specific type.
     *
     * @return {@code true} if value matches the type
     */
    private static Function typeIs() {
        return args -> {
            if (args.length < 2) return false;
            String type = String.valueOf(args[0]).toLowerCase();
            Object val = args[1];

            if (val == null) return "nil".equals(type);

            String className = val.getClass().getSimpleName().toLowerCase();
            String fullName = val.getClass().getName().toLowerCase();

            return className.equals(type) || fullName.contains(type);
        };
    }

    /**
     * Checks if the value's type is like a specific type (partial match).
     *
     * @return {@code true} if value's type matches the pattern
     */
    private static Function typeIsLike() {
        return args -> {
            if (args.length < 2) return false;
            String typePattern = String.valueOf(args[0]).toLowerCase();
            Object val = args[1];

            if (val == null) return "nil".equals(typePattern);

            String fullName = val.getClass().getName().toLowerCase();
            return fullName.contains(typePattern);
        };
    }

    /**
     * Checks if the value's kind matches a specific kind.
     *
     * @return {@code true} if value's kind matches
     */
    private static Function kindIs() {
        return args -> {
            if (args.length < 2) return false;
            String kind = String.valueOf(args[0]);
            Object val = args[1];

            if (val == null) return "invalid".equals(kind);

            Class<?> c = val.getClass();
            return switch (kind) {
                case "string" -> c == String.class;
                case "number", "int", "float", "float64" -> Number.class.isAssignableFrom(c);
                case "bool" -> Boolean.class.isAssignableFrom(c);
                case "map" -> Map.class.isAssignableFrom(c);
                case "slice", "list" -> Collection.class.isAssignableFrom(c) || c.isArray();
                default -> false;
            };
        };
    }

    // ========== Equality Functions ==========

    /**
     * Performs deep equality comparison between two values.
     * Recursively compares Maps, Collections, and arrays.
     *
     * @return {@code true} if values are deeply equal
     */
    private static Function deepEqual() {
        return args -> {
            if (args.length < 2) return false;
            return deepEquals(args[0], args[1]);
        };
    }

    // ========== Helper Methods ==========

    /**
     * Recursively compares two objects for deep equality.
     */
    private static boolean deepEquals(Object a, Object b) {
        // Null checks
        if (a == b) return true;
        if (a == null || b == null) return false;

        // Class mismatch
        if (!a.getClass().equals(b.getClass())) return false;

        // Maps
        if (a instanceof Map) {
            Map<?, ?> mapA = (Map<?, ?>) a;
            Map<?, ?> mapB = (Map<?, ?>) b;

            if (mapA.size() != mapB.size()) return false;

            for (Map.Entry<?, ?> entry : mapA.entrySet()) {
                Object key = entry.getKey();
                if (!mapB.containsKey(key)) return false;
                if (!deepEquals(entry.getValue(), mapB.get(key))) return false;
            }
            return true;
        }

        // Collections
        if (a instanceof Collection) {
            Collection<?> colA = (Collection<?>) a;
            Collection<?> colB = (Collection<?>) b;

            if (colA.size() != colB.size()) return false;

            Iterator<?> iterA = colA.iterator();
            Iterator<?> iterB = colB.iterator();

            while (iterA.hasNext() && iterB.hasNext()) {
                if (!deepEquals(iterA.next(), iterB.next())) return false;
            }
            return true;
        }

        // Arrays
        if (a.getClass().isArray()) {
            int lengthA = java.lang.reflect.Array.getLength(a);
            int lengthB = java.lang.reflect.Array.getLength(b);

            if (lengthA != lengthB) return false;

            for (int i = 0; i < lengthA; i++) {
                Object elemA = java.lang.reflect.Array.get(a, i);
                Object elemB = java.lang.reflect.Array.get(b, i);
                if (!deepEquals(elemA, elemB)) return false;
            }
            return true;
        }

        // Primitive types and other objects
        return Objects.equals(a, b);
    }
}
