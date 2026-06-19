package org.alexmond.jhelm.gotemplate;

import java.lang.reflect.Array;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Functions {

	/**
	 * Go built-in template functions (Go {@code text/template} builtins). Does not
	 * include Sprig or Helm functions — those are loaded via {@link FunctionProvider}
	 * implementations.
	 */
	public static final Map<String, Function> GO_BUILTINS;

	static {
		LinkedHashMap<String, Function> go = new LinkedHashMap<>();
		go.put("call", call());
		go.put("html", htmlEscape());
		go.put("index", index());
		go.put("slice", slice());
		go.put("js", jsEscape());
		go.put("len", len());
		go.put("print", print());
		go.put("printf", printf());
		go.put("println", println());
		go.put("urlquery", urlquery());

		// Logical operations
		go.put("and", and());
		go.put("or", or());
		go.put("not", not());

		// Comparisons
		go.put("eq", eq());
		go.put("ge", ge());
		go.put("gt", gt());
		go.put("le", le());
		go.put("lt", lt());
		go.put("ne", ne());

		GO_BUILTINS = Map.copyOf(go);
	}

	private Functions() {
	}

	private static Function index() {
		return (args) -> {
			if (args.length == 0) {
				return null;
			}
			// Go's `index x` with no keys returns x itself; only indexing keys descend.
			Object result = args[0];
			for (int i = 1; i < args.length; i++) {
				Object key = args[i];
				if (result instanceof Map) {
					if (key == null) {
						return null;
					}
					result = ((Map<?, ?>) result).get(key);
				}
				else if (result instanceof List) {
					result = ((List<?>) result).get(((Number) key).intValue());
				}
				else if (result != null && result.getClass().isArray()) {
					result = Array.get(result, ((Number) key).intValue());
				}
				else {
					return null;
				}
			}
			return result;
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
				throw new FunctionExecutionException("call: first argument must be a function");
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
		return (args) -> Arrays.stream(args).map(Functions::sprintValue).collect(Collectors.joining(""));
	}

	private static Function printf() {
		return (args) -> {
			if (args.length == 0) {
				return "";
			}
			String format = String.valueOf(args[0]);
			// Verbs as written, before translation — needed to render a nil argument the
			// way Go does (it keys the marker off the original verb, e.g. %v vs %s).
			List<Character> origVerbs = extractVerbs(format);

			// Translate Go format verbs to Java equivalents
			format = format.replaceAll("%#?v", "%s"); // %v and %#(v) -> %s
			format = format.replaceAll("%T", "%s"); // %(T) -> %s
			format = format.replaceAll("%p", "%s"); // %(p) -> %s
			format = format.replaceAll("%t", "%b"); // Go bool verb %t -> Java %b (Java %t
													// is dates)
			// %q is kept for now — unlike %s it double-quotes its argument (Go strconv.
			// Quote), so the corresponding arg is quoted below before %q -> %s.

			// Extract ALL format specifiers to coerce numeric types per-argument.
			// Go's printf is lenient (any numeric type for %d/%g); Java is strict.
			// Must track non-numeric specs (%s, %c, etc.) to maintain correct
			// positional alignment with arguments.
			List<Character> specTypes = extractVerbs(format);

			// Positions holding a literal Go bad-verb marker; their specifier is forced
			// to
			// %s so String.format prints the marker text verbatim.
			Set<Integer> markerSlots = new HashSet<>();
			Object[] realArgs = buildPrintfArgs(args, specTypes, origVerbs, markerSlots);
			// The %q arguments are now pre-quoted strings, so render them with %s.
			format = format.replaceAll("%q", "%s");
			if (!markerSlots.isEmpty()) {
				format = forceSpecifiersToString(format, markerSlots);
			}
			try {
				return String.format(format, realArgs);
			}
			catch (MissingFormatArgumentException ex) {
				// Go's printf tolerates more verbs than arguments, emitting
				// `%!verb(MISSING)` for each surplus verb instead of aborting (e.g.
				// bitnami
				// harbor's noProxy helper passes 7 args to 11 %s verbs). Substitute the
				// Go
				// markers and retry; fall back to neutralising if other bad verbs remain.
				String filled = fillMissingSpecifiers(format, realArgs.length);
				try {
					return String.format(filled, realArgs);
				}
				catch (IllegalFormatException ex2) {
					return String.format(neutralizeInvalidFormatSpecifiers(filled), realArgs);
				}
			}
			catch (IllegalFormatException ex) {
				// Go's fmt never aborts on malformed verbs (it emits best-effort
				// markers),
				// but Java's Formatter throws. ERB-style literals routed through printf —
				// e.g. gitlab's "<%= File.read('%s')... %>" where %= and %> are not Java
				// conversions — must not crash rendering. Neutralise the unparseable '%'
				// sequences (keeping the real verbs) and retry.
				return String.format(neutralizeInvalidFormatSpecifiers(format), realArgs);
			}
		};
	}

	/**
	 * Builds the {@link String#format} argument array for {@code printf}, mapping each
	 * argument to its verb: a {@code null} becomes Go's nil marker on a string slot, a
	 * non-numeric value on a numeric verb becomes Go's {@code %!verb(type=value)} marker
	 * (its index recorded in {@code markerSlots} so its specifier is later forced to
	 * {@code %s}), and everything else is coerced by {@link #coercePrintfArg}.
	 * @param args the raw {@code printf} arguments ({@code args[0]} is the format)
	 * @param specTypes the translated verbs, one per argument slot
	 * @param origVerbs the original (pre-translation) verbs
	 * @param markerSlots out-param collecting positions that hold a literal marker
	 * @return the coerced argument array
	 */
	private static Object[] buildPrintfArgs(Object[] args, List<Character> specTypes, List<Character> origVerbs,
			Set<Integer> markerSlots) {
		Object[] realArgs = new Object[args.length - 1];
		for (int i = 0; i < realArgs.length; i++) {
			char spec = (i < specTypes.size()) ? specTypes.get(i) : '\0';
			char ov = (i < origVerbs.size()) ? origVerbs.get(i) : spec;
			Object raw = args[i + 1];
			if (raw == null) {
				// Go prints a bad-verb marker for a nil argument: %v -> <nil>, every
				// other
				// verb -> %!verb(<nil>). We only have a plain-string slot here (spec 's',
				// also the rewritten %v), so emit the marker as text; other verbs keep
				// the
				// historical empty-string rendering.
				realArgs[i] = (spec == 's') ? goNilMarker(ov) : "";
			}
			else if ("doxXeEfgG".indexOf(spec) >= 0 && !(raw instanceof Number)) {
				// Go prints %!verb(gotype=value) for a non-numeric arg on a numeric verb
				// (e.g. yugabyte's `printf "%d" "4Gi"` -> %!d(string=4Gi), whose digits a
				// following regexFind extracts). Java's Formatter throws, so substitute
				// the
				// marker and render it through %s.
				realArgs[i] = "%!" + ov + "(" + goTypeName(raw) + "=" + raw + ")";
				markerSlots.add(i);
			}
			else {
				realArgs[i] = coercePrintfArg(raw, spec);
			}
		}
		return realArgs;
	}

	/**
	 * Extracts the conversion verb (trailing letter) of each {@code %} specifier in a
	 * format string, in order, skipping escaped {@code %%}. Used to align arguments with
	 * their verbs for per-argument coercion.
	 * @param format the format string
	 * @return the verbs in left-to-right order
	 */
	private static List<Character> extractVerbs(String format) {
		Matcher m = Pattern.compile("%[^%]*?([a-zA-Z])").matcher(format);
		List<Character> verbs = new ArrayList<>();
		while (m.find()) {
			verbs.add(m.group(1).charAt(0));
		}
		return verbs;
	}

	/**
	 * Renders Go's bad-verb marker for a {@code nil} argument: {@code %v} yields
	 * {@code <nil>}; every other verb yields {@code %!verb(<nil>)}.
	 * @param verb the original (pre-translation) format verb
	 * @return the Go nil marker text
	 */
	private static String goNilMarker(char verb) {
		return (verb == 'v') ? "<nil>" : "%!" + verb + "(<nil>)";
	}

	/**
	 * Go type name used inside a bad-verb marker (e.g. {@code %!d(string=4Gi)}).
	 * @param value the offending argument
	 * @return the Go type name for {@code value}
	 */
	private static String goTypeName(Object value) {
		if (value instanceof String) {
			return "string";
		}
		if (value instanceof Boolean) {
			return "bool";
		}
		if (value instanceof Double || value instanceof Float) {
			return "float64";
		}
		if (value instanceof Integer || value instanceof Long) {
			return "int";
		}
		if (value instanceof Map) {
			return "map[string]interface {}";
		}
		if (value instanceof List) {
			return "[]interface {}";
		}
		return "interface {}";
	}

	/**
	 * Rewrites the format specifiers at the given argument positions to a bare
	 * {@code %s}, so a pre-formatted literal (a Go bad-verb marker) prints verbatim.
	 * @param format the (already Java-translated) format string
	 * @param positions the zero-based specifier indices to force to {@code %s}
	 * @return the format with those specifiers replaced by {@code %s}
	 */
	private static String forceSpecifiersToString(String format, Set<Integer> positions) {
		Matcher m = Pattern.compile("%[^%]*?[a-zA-Z]").matcher(format);
		StringBuilder sb = new StringBuilder(format.length());
		int idx = 0;
		while (m.find()) {
			if (positions.contains(idx)) {
				m.appendReplacement(sb, "%s");
			}
			idx++;
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Coerces a single {@code printf} argument so Java's {@link String#format} matches
	 * Go's {@code fmt} for the given (already Java-translated) verb: {@code %q}
	 * pre-quotes, integer/float verbs widen/narrow as Go would, and {@code %s} (also the
	 * rewritten {@code %v}) renders a whole {@code float64} without a trailing
	 * {@code .0}.
	 * @param arg the argument value (never {@code null} — callers pass {@code ""})
	 * @param spec the format verb the argument is bound to
	 * @return the coerced argument
	 */
	private static Object coercePrintfArg(Object arg, char spec) {
		if (spec == 'q') {
			return goQuote(arg);
		}
		if (!(arg instanceof Number)) {
			return arg;
		}
		if ("eEfgG".indexOf(spec) >= 0 && (arg instanceof Integer || arg instanceof Long)) {
			return ((Number) arg).doubleValue();
		}
		if ("doxXb".indexOf(spec) >= 0 && (arg instanceof Double || arg instanceof Float)) {
			return ((Number) arg).longValue();
		}
		if (spec == 's' && (arg instanceof Double || arg instanceof Float)) {
			double d = ((Number) arg).doubleValue();
			if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
				return (long) d;
			}
		}
		return arg;
	}

	/**
	 * Replaces format specifiers that have no corresponding argument with Go's
	 * {@code %!verb(MISSING)} marker, so a {@code printf} with more verbs than arguments
	 * renders like Go instead of throwing. The surplus markers are emitted as escaped
	 * literals ({@code %%...}) so the subsequent {@link String#format} passes them
	 * through.
	 * @param format the (already Java-translated) format string
	 * @param argCount the number of available arguments
	 * @return the format string with surplus verbs turned into literal MISSING markers
	 */
	private static String fillMissingSpecifiers(String format, int argCount) {
		Matcher m = Pattern.compile("%[^%]*?([a-zA-Z])").matcher(format);
		StringBuilder sb = new StringBuilder(format.length());
		int idx = 0;
		while (m.find()) {
			if (idx >= argCount) {
				m.appendReplacement(sb, Matcher.quoteReplacement("%%!" + m.group(1) + "(MISSING)"));
			}
			idx++;
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Renders a value the way Go's {@code %q} verb does: the string form wrapped in
	 * double quotes with Go-syntax escaping of quotes, backslashes and the common control
	 * characters. Used so {@code printf "%q" x} matches Helm/Go output.
	 */
	private static String goQuote(Object value) {
		String s = String.valueOf(value);
		StringBuilder sb = new StringBuilder(s.length() + 2);
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\t' -> sb.append("\\t");
				case '\r' -> sb.append("\\r");
				default -> sb.append(c);
			}
		}
		sb.append('"');
		return sb.toString();
	}

	/**
	 * Replaces every {@code %} that does not begin a valid Java format conversion (or
	 * {@code %%}) with a literal {@code %%}, leaving genuine verbs (and their flags,
	 * width and precision) intact. This lets {@link String#format} tolerate Go/ERB format
	 * strings such as {@code "<%= ... %>"} without throwing, mirroring Go's lenient fmt.
	 */
	private static String neutralizeInvalidFormatSpecifiers(String format) {
		final String flags = "-+ #0,(";
		final String validVerbs = "bBhHsScCdoxXeEfgGaAn";
		StringBuilder out = new StringBuilder(format.length() + 8);
		int i = 0;
		int n = format.length();
		while (i < n) {
			char c = format.charAt(i);
			if (c != '%') {
				out.append(c);
				i++;
				continue;
			}
			if (i + 1 < n && format.charAt(i + 1) == '%') {
				out.append("%%");
				i += 2;
				continue;
			}
			int j = i + 1;
			// optional argument index "k$"
			int k = j;
			while (k < n && Character.isDigit(format.charAt(k))) {
				k++;
			}
			if (k < n && k > j && format.charAt(k) == '$') {
				j = k + 1;
			}
			while (j < n && flags.indexOf(format.charAt(j)) >= 0) {
				j++;
			}
			while (j < n && Character.isDigit(format.charAt(j))) {
				j++;
			}
			if (j < n && format.charAt(j) == '.') {
				j++;
				while (j < n && Character.isDigit(format.charAt(j))) {
					j++;
				}
			}
			if (j < n && validVerbs.indexOf(format.charAt(j)) >= 0) {
				out.append(format, i, j + 1);
				i = j + 1;
			}
			else {
				// Not a valid conversion: treat this '%' as a literal.
				out.append("%%");
				i++;
			}
		}
		return out.toString();
	}

	private static Function println() {
		return (args) -> Arrays.stream(args).map(Functions::sprintValue).collect(Collectors.joining(" ")) + "\n";
	}

	private static Function not() {
		return (args) -> args.length > 0 && !isTrue(args[0]);
	}

	/**
	 * Convert a value to its string representation for print/println. Matches Go's
	 * {@code fmt.Sprint(nil)} which produces {@code "<nil>"} for nil values.
	 */
	static String sprintValue(Object value) {
		return (value != null) ? String.valueOf(value) : "<nil>";
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
