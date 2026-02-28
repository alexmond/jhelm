package org.alexmond.jhelm.gotemplate;

import java.lang.reflect.Array;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.alexmond.jhelm.gotemplate.sprig.SprigFunctionsRegistry;

public final class Functions {

	public static final Map<String, Function> BUILTIN = new LinkedHashMap<>();

	static {
		BUILTIN.put("call", call());
		BUILTIN.put("html", htmlEscape());
		BUILTIN.put("index", index());
		BUILTIN.put("slice", slice());
		BUILTIN.put("js", jsEscape());
		BUILTIN.put("len", len());
		BUILTIN.put("print", print());
		BUILTIN.put("printf", printf());
		BUILTIN.put("println", println());
		BUILTIN.put("urlquery", urlquery());

		// Logical operations
		BUILTIN.put("and", and());
		BUILTIN.put("or", or());
		BUILTIN.put("not", not());

		// Comparisons
		BUILTIN.put("eq", eq());
		BUILTIN.put("ge", ge());
		BUILTIN.put("gt", gt());
		BUILTIN.put("le", le());
		BUILTIN.put("lt", lt());
		BUILTIN.put("ne", ne());

		// Load Sprig functions
		BUILTIN.putAll(SprigFunctionsRegistry.getAllFunctions());
	}

	private Functions() {
	}

	private static Function index() {
		return (args) -> {
			if (args.length < 2) {
				return null;
			}
			Object container = args[0];
			Object key = args[1];
			if (container instanceof Map) {
				return ((Map<?, ?>) container).get(key);
			}
			if (container instanceof List) {
				return ((List<?>) container).get(((Number) key).intValue());
			}
			if (container != null && container.getClass().isArray()) {
				return Array.get(container, ((Number) key).intValue());
			}
			return null;
		};
	}

	private static Function len() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return 0;
			}
			Object o = args[0];
			if (o instanceof Collection) {
				return ((Collection<?>) o).size();
			}
			if (o instanceof Map) {
				return ((Map<?, ?>) o).size();
			}
			if (o instanceof String) {
				return ((String) o).length();
			}
			if (o.getClass().isArray()) {
				return Array.getLength(o);
			}
			return 0;
		};
	}

	private static Function and() {
		return (args) -> {
			for (Object arg : args) {
				if (!isTrue(arg)) {
					return arg;
				}
			}
			return (args.length > 0) ? args[args.length - 1] : false;
		};
	}

	private static Function or() {
		return (args) -> {
			for (Object arg : args) {
				if (isTrue(arg)) {
					return arg;
				}
			}
			return (args.length > 0) ? args[args.length - 1] : false;
		};
	}

	private static boolean valuesEqual(Object a, Object b) {
		if (a instanceof Number && b instanceof Number) {
			return ((Number) a).doubleValue() == ((Number) b).doubleValue();
		}
		return Objects.equals(a, b);
	}

	private static Function eq() {
		return (args) -> {
			if (args.length < 2) {
				return false;
			}
			Object first = args[0];
			for (int i = 1; i < args.length; i++) {
				if (!valuesEqual(first, args[i])) {
					return false;
				}
			}
			return true;
		};
	}

	private static Function ne() {
		return (args) -> args.length >= 2 && !valuesEqual(args[0], args[1]);
	}

	private static Function lt() {
		return (args) -> {
			if (args.length < 2) {
				return false;
			}
			if (args[0] == null || args[1] == null) {
				return false;
			}

			// Handle numeric comparisons by converting to double
			if (args[0] instanceof Number && args[1] instanceof Number) {
				double v1 = ((Number) args[0]).doubleValue();
				double v2 = ((Number) args[1]).doubleValue();
				return v1 < v2;
			}

			// Handle string and other comparable types
			if (!(args[0] instanceof Comparable)) {
				return false;
			}
			try {
				@SuppressWarnings("unchecked")
				Comparable<Object> comparable = (Comparable<Object>) args[0];
				return comparable.compareTo(args[1]) < 0;
			}
			catch (ClassCastException ex) {
				return false;
			}
		};
	}

	private static Function le() {
		return (args) -> {
			if (args.length < 2) {
				return false;
			}
			if (args[0] == null || args[1] == null) {
				return false;
			}

			// Handle numeric comparisons by converting to double
			if (args[0] instanceof Number && args[1] instanceof Number) {
				double v1 = ((Number) args[0]).doubleValue();
				double v2 = ((Number) args[1]).doubleValue();
				return v1 <= v2;
			}

			// Handle string and other comparable types
			if (!(args[0] instanceof Comparable)) {
				return false;
			}
			try {
				@SuppressWarnings("unchecked")
				Comparable<Object> comparable = (Comparable<Object>) args[0];
				return comparable.compareTo(args[1]) <= 0;
			}
			catch (ClassCastException ex) {
				return false;
			}
		};
	}

	private static Function gt() {
		return (args) -> {
			if (args.length < 2) {
				return false;
			}
			if (args[0] == null || args[1] == null) {
				return false;
			}

			// Handle numeric comparisons by converting to double
			if (args[0] instanceof Number && args[1] instanceof Number) {
				double v1 = ((Number) args[0]).doubleValue();
				double v2 = ((Number) args[1]).doubleValue();
				return v1 > v2;
			}

			// Handle string and other comparable types
			if (!(args[0] instanceof Comparable)) {
				return false;
			}
			try {
				@SuppressWarnings("unchecked")
				Comparable<Object> comparable = (Comparable<Object>) args[0];
				return comparable.compareTo(args[1]) > 0;
			}
			catch (ClassCastException ex) {
				return false;
			}
		};
	}

	private static Function ge() {
		return (args) -> {
			if (args.length < 2) {
				return false;
			}
			if (args[0] == null || args[1] == null) {
				return false;
			}

			// Handle numeric comparisons by converting to double
			if (args[0] instanceof Number && args[1] instanceof Number) {
				double v1 = ((Number) args[0]).doubleValue();
				double v2 = ((Number) args[1]).doubleValue();
				return v1 >= v2;
			}

			// Handle string and other comparable types
			if (!(args[0] instanceof Comparable)) {
				return false;
			}
			try {
				@SuppressWarnings("unchecked")
				Comparable<Object> comparable = (Comparable<Object>) args[0];
				return comparable.compareTo(args[1]) >= 0;
			}
			catch (ClassCastException ex) {
				return false;
			}
		};
	}

	private static Function call() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return null;
			}
			if (!(args[0] instanceof Function fn)) {
				throw new RuntimeException("call: first argument must be a function");
			}
			Object[] fnArgs = new Object[args.length - 1];
			System.arraycopy(args, 1, fnArgs, 0, args.length - 1);
			return fn.invoke(fnArgs);
		};
	}

	private static Function htmlEscape() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "";
			}
			String s = String.valueOf(args[0]);
			StringBuilder sb = new StringBuilder(s.length());
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				switch (c) {
					case '&' -> sb.append("&amp;");
					case '<' -> sb.append("&lt;");
					case '>' -> sb.append("&gt;");
					case '"' -> sb.append("&#34;");
					case '\'' -> sb.append("&#39;");
					default -> sb.append(c);
				}
			}
			return sb.toString();
		};
	}

	private static Function jsEscape() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "";
			}
			String s = String.valueOf(args[0]);
			StringBuilder sb = new StringBuilder(s.length());
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				switch (c) {
					case '\\' -> sb.append("\\\\");
					case '\'' -> sb.append("\\'");
					case '"' -> sb.append("\\\"");
					case '\n' -> sb.append("\\n");
					case '\r' -> sb.append("\\r");
					case '\t' -> sb.append("\\t");
					case '<' -> sb.append("\\u003C");
					case '>' -> sb.append("\\u003E");
					case '&' -> sb.append("\\u0026");
					case '=' -> sb.append("\\u003D");
					default -> sb.append(c);
				}
			}
			return sb.toString();
		};
	}

	@SuppressWarnings("unchecked")
	private static Function slice() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return null;
			}
			Object container = args[0];
			int size;
			if (container instanceof List) {
				size = ((List<?>) container).size();
			}
			else if (container.getClass().isArray()) {
				size = Array.getLength(container);
			}
			else {
				return null;
			}
			int from = (args.length > 1) ? ((Number) args[1]).intValue() : 0;
			int to = (args.length > 2) ? ((Number) args[2]).intValue() : size;
			if (container instanceof List) {
				return new ArrayList<>(((List<Object>) container).subList(from, to));
			}
			Object[] result = new Object[to - from];
			for (int i = from; i < to; i++) {
				result[i - from] = Array.get(container, i);
			}
			return List.of(result);
		};
	}

	private static Function urlquery() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "";
			}
			return URLEncoder.encode(String.valueOf(args[0]), StandardCharsets.UTF_8);
		};
	}

	private static Function print() {
		// In Go, fmt.Sprint adds spaces between operands when neither is a string.
		// For Helm templates, all values are typically strings, so concatenate without
		// spaces.
		// NOTE: This fixes grafana/grafana but may cause issues with charts using Bitnami
		// common helpers
		// that rely on specific print behavior with regexReplaceAll
		return (args) -> Arrays.stream(args).map(String::valueOf).collect(Collectors.joining(""));
	}

	private static Function printf() {
		return (args) -> {
			if (args.length == 0) {
				return "";
			}
			String format = String.valueOf(args[0]);

			// Translate Go format verbs to Java equivalents
			// %(v) -> %s (default format)
			// %#(v) -> %s (Go-syntax representation, simplified to string)
			// %(T) -> %s (type, simplified to string representation)
			// %(t) -> %b (boolean)
			// %(b) -> %s (binary, not directly supported, use string)
			// %(c) -> %c (character - keep as is)
			// %d, %o, %x, %(X) -> keep as is (integer formats)
			// %e, %E, %f, %F, %g, %(G) -> keep as is (float formats)
			// %(s) -> %s (string - keep as is)
			// %(q) -> %s (quoted string, simplified)
			// %(p) -> %s (pointer, simplified)
			format = format.replaceAll("%#?v", "%s"); // %v and %#(v) -> %s
			format = format.replaceAll("%T", "%s"); // %(T) -> %s
			format = format.replaceAll("%q", "%s"); // %(q) -> %s
			format = format.replaceAll("%p", "%s"); // %(p) -> %s

			Object[] realArgs = new Object[args.length - 1];
			System.arraycopy(args, 1, realArgs, 0, args.length - 1);
			return String.format(format, realArgs);
		};
	}

	private static Function println() {
		return (args) -> Arrays.stream(args).map(String::valueOf).collect(Collectors.joining(" ")) + "\n";
	}

	private static Function not() {
		return (args) -> args.length > 0 && !isTrue(args[0]);
	}

	public static boolean isTrue(Object arg) {
		if (arg == null) {
			return false;
		}
		if (arg instanceof Boolean) {
			return (Boolean) arg;
		}
		if (arg instanceof String) {
			return !((String) arg).isEmpty();
		}
		if (arg instanceof Number) {
			return ((Number) arg).doubleValue() != 0;
		}
		if (arg instanceof Collection) {
			return !((Collection<?>) arg).isEmpty();
		}
		if (arg instanceof Map) {
			return !((Map<?, ?>) arg).isEmpty();
		}
		return !arg.getClass().isArray() || Array.getLength(arg) > 0;
	}

}
