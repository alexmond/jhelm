package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import org.alexmond.jhelm.gotemplate.Function;
import java.lang.reflect.Array;

/**
 * Type reflection and introspection functions from Sprig library. Includes type checking,
 * kind checking, and deep equality comparison.
 *
 * @see <a href="https://masterminds.github.io/sprig/reflection.html">Sprig Reflection
 * Functions</a>
 */
public final class ReflectionFunctions {

	private ReflectionFunctions() {
	}

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
	 * Returns the Go-style type name of the value. Maps Java types to their Go
	 * equivalents to match Sprig's {@code typeOf} behavior (which uses Go's
	 * {@code fmt.Sprintf("%T", src)}).
	 */
	private static Function typeOf() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "<nil>";
			}
			return goTypeName(args[0]);
		};
	}

	/**
	 * Returns the kind (basic type category) of the value using Go's {@code reflect.Kind}
	 * names.
	 */
	private static Function kindOf() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "invalid";
			}
			return goKindName(args[0]);
		};
	}

	// ========== Type Checking Functions ==========

	/**
	 * Checks if the value is of a specific type. Uses exact match against the Go-style
	 * type name, matching Sprig's {@code typeIs} behavior.
	 * @return {@code true} if value matches the type
	 */
	private static Function typeIs() {
		return (args) -> {
			if (args.length < 2) {
				return false;
			}
			String target = String.valueOf(args[0]);
			Object val = args[1];
			return target.equals(goTypeName(val));
		};
	}

	/**
	 * Checks if the value's type matches a specific type or its pointer variant. In Go,
	 * {@code typeIsLike} checks
	 * {@code target == typeOf(src) || "*"+target == typeOf(src)}.
	 * @return {@code true} if value's type matches the pattern
	 */
	private static Function typeIsLike() {
		return (args) -> {
			if (args.length < 2) {
				return false;
			}
			String target = String.valueOf(args[0]);
			String typeName = goTypeName(args[1]);
			return target.equals(typeName) || ("*" + target).equals(typeName);
		};
	}

	/**
	 * Checks if the value's kind matches a specific kind. Matches against Go's
	 * {@code reflect.Kind} names with additional aliases for numeric types.
	 * @return {@code true} if value's kind matches
	 */
	private static Function kindIs() {
		return (args) -> {
			if (args.length < 2) {
				return false;
			}
			String kind = String.valueOf(args[0]);
			Object val = args[1];

			if (val == null) {
				return "invalid".equals(kind);
			}

			String actualKind = goKindName(val);
			if (kind.equals(actualKind)) {
				return true;
			}
			// Accept aliases: "number" matches any numeric kind, "list" matches "slice"
			Class<?> c = val.getClass();
			return switch (kind) {
				case "number" -> Number.class.isAssignableFrom(c);
				case "list" -> Collection.class.isAssignableFrom(c) || c.isArray();
				default -> false;
			};
		};
	}

	// ========== Go Type Mapping ==========

	/**
	 * Maps a Java object to its Go-style type name, matching what Go's
	 * {@code fmt.Sprintf("%T", src)} would return.
	 */
	static String goTypeName(Object obj) {
		if (obj == null) {
			return "<nil>";
		}
		Class<?> c = obj.getClass();
		if (c == String.class) {
			return "string";
		}
		if (c == Boolean.class) {
			return "bool";
		}
		if (c == Integer.class || c == Short.class || c == Byte.class) {
			return "int";
		}
		if (c == Long.class || c == BigInteger.class) {
			return "int64";
		}
		if (c == Double.class || c == BigDecimal.class) {
			return "float64";
		}
		if (c == Float.class) {
			return "float32";
		}
		if (Map.class.isAssignableFrom(c)) {
			return "map[string]interface {}";
		}
		if (Collection.class.isAssignableFrom(c) || c.isArray()) {
			return "[]interface {}";
		}
		return c.getName();
	}

	/**
	 * Maps a Java object to its Go-style kind name, matching Go's
	 * {@code reflect.ValueOf(src).Kind().String()}.
	 */
	private static String goKindName(Object obj) {
		Class<?> c = obj.getClass();
		if (c == String.class) {
			return "string";
		}
		if (c == Boolean.class) {
			return "bool";
		}
		if (c == Integer.class || c == Short.class || c == Byte.class) {
			return "int";
		}
		if (c == Long.class || c == BigInteger.class) {
			return "int64";
		}
		if (c == Double.class || c == BigDecimal.class) {
			return "float64";
		}
		if (c == Float.class) {
			return "float32";
		}
		if (Map.class.isAssignableFrom(c)) {
			return "map";
		}
		if (Collection.class.isAssignableFrom(c) || c.isArray()) {
			return "slice";
		}
		return "struct";
	}

	// ========== Equality Functions ==========

	/**
	 * Performs deep equality comparison between two values. Recursively compares Maps,
	 * Collections, and arrays.
	 * @return {@code true} if values are deeply equal
	 */
	private static Function deepEqual() {
		return (args) -> args.length >= 2 && deepEquals(args[0], args[1]);
	}

	// ========== Helper Methods ==========

	/**
	 * Recursively compares two objects for deep equality.
	 */
	private static boolean deepEquals(Object a, Object b) {
		// Null checks
		if (Objects.equals(a, b)) {
			return true;
		}
		if (a == null || b == null) {
			return false;
		}

		// Class mismatch
		if (!a.getClass().equals(b.getClass())) {
			return false;
		}

		// Maps
		if (a instanceof Map) {
			Map<?, ?> mapA = (Map<?, ?>) a;
			Map<?, ?> mapB = (Map<?, ?>) b;

			if (mapA.size() != mapB.size()) {
				return false;
			}

			for (Map.Entry<?, ?> entry : mapA.entrySet()) {
				Object key = entry.getKey();
				if (!mapB.containsKey(key)) {
					return false;
				}
				if (!deepEquals(entry.getValue(), mapB.get(key))) {
					return false;
				}
			}
			return true;
		}

		// Collections
		if (a instanceof Collection) {
			Collection<?> colA = (Collection<?>) a;
			Collection<?> colB = (Collection<?>) b;

			if (colA.size() != colB.size()) {
				return false;
			}

			Iterator<?> iterA = colA.iterator();
			Iterator<?> iterB = colB.iterator();

			while (iterA.hasNext() && iterB.hasNext()) {
				if (!deepEquals(iterA.next(), iterB.next())) {
					return false;
				}
			}
			return true;
		}

		// Arrays
		if (a.getClass().isArray()) {
			int lengthA = Array.getLength(a);
			int lengthB = Array.getLength(b);

			if (lengthA != lengthB) {
				return false;
			}

			for (int i = 0; i < lengthA; i++) {
				Object elemA = Array.get(a, i);
				Object elemB = Array.get(b, i);
				if (!deepEquals(elemA, elemB)) {
					return false;
				}
			}
			return true;
		}

		// Primitive types and other objects
		return Objects.equals(a, b);
	}

}
