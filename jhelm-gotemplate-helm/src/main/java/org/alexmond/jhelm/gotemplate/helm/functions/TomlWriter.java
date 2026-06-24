package org.alexmond.jhelm.gotemplate.helm.functions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Serializes a value to TOML the way Helm's {@code toToml} does, i.e. matching Go's
 * {@code github.com/BurntSushi/toml} encoder rather than Jackson's dotted-key output.
 * <p>
 * The encoder reproduces BurntSushi's structural choices observed against the
 * {@code helm} binary:
 * <ul>
 * <li>keys are sorted alphabetically, with scalar/inline-array fields emitted before
 * sub-tables;</li>
 * <li>a table's key/value pairs are indented {@code depth * 2} spaces and its
 * {@code [header]} by {@code (depth - 1) * 2};</li>
 * <li>a blank line precedes every <em>top-level</em> table and every array-of-tables
 * element (but never the first line of output);</li>
 * <li>strings are basic (double-quoted) strings, floats always carry a decimal
 * point.</li>
 * </ul>
 * A non-table top-level value yields an empty string, matching Helm (BurntSushi errors
 * and Helm discards the error).
 */
final class TomlWriter {

	private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");

	private TomlWriter() {
	}

	/**
	 * Serialize a value to a TOML document.
	 * @param root the value to serialize (only a map produces output)
	 * @return the TOML text, or {@code ""} if {@code root} is not a map
	 */
	static String write(Object root) {
		if (!(root instanceof Map<?, ?> map)) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		writeTable(sb, new ArrayList<>(), map);
		return sb.toString();
	}

	private static void writeTable(StringBuilder sb, List<String> key, Map<?, ?> map) {
		List<String> directKeys = new ArrayList<>();
		List<String> tableKeys = new ArrayList<>();
		for (Object rawKey : map.keySet()) {
			String name = String.valueOf(rawKey);
			if (isTable(map.get(rawKey))) {
				tableKeys.add(name);
			}
			else {
				directKeys.add(name);
			}
		}
		Collections.sort(directKeys);
		Collections.sort(tableKeys);

		String indent = "  ".repeat(key.size());
		for (String name : directKeys) {
			sb.append(indent).append(formatKey(name)).append(" = ").append(formatValue(map.get(name))).append('\n');
		}
		for (String name : tableKeys) {
			Object value = map.get(name);
			List<String> childKey = new ArrayList<>(key);
			childKey.add(name);
			String header = "  ".repeat(childKey.size() - 1);
			if (isArrayOfTables(value)) {
				for (Object element : (List<?>) value) {
					separator(sb);
					sb.append(header).append("[[").append(joinKey(childKey)).append("]]\n");
					writeTable(sb, childKey, (Map<?, ?>) element);
				}
			}
			else {
				if (childKey.size() == 1) {
					separator(sb);
				}
				sb.append(header).append('[').append(joinKey(childKey)).append("]\n");
				writeTable(sb, childKey, (Map<?, ?>) value);
			}
		}
	}

	/** Write a blank-line separator, unless nothing has been written yet. */
	private static void separator(StringBuilder sb) {
		if (sb.length() > 0) {
			sb.append('\n');
		}
	}

	private static boolean isTable(Object value) {
		return value instanceof Map || isArrayOfTables(value);
	}

	private static boolean isArrayOfTables(Object value) {
		if (!(value instanceof List<?> list) || list.isEmpty()) {
			return false;
		}
		for (Object element : list) {
			if (!(element instanceof Map)) {
				return false;
			}
		}
		return true;
	}

	private static String joinKey(List<String> key) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < key.size(); i++) {
			if (i > 0) {
				sb.append('.');
			}
			sb.append(formatKey(key.get(i)));
		}
		return sb.toString();
	}

	private static String formatKey(String key) {
		return BARE_KEY.matcher(key).matches() ? key : quote(key);
	}

	private static String formatValue(Object value) {
		if (value instanceof String str) {
			return quote(str);
		}
		if (value instanceof Boolean bool) {
			return bool.toString();
		}
		if (value instanceof Double || value instanceof Float) {
			return formatFloat(((Number) value).doubleValue());
		}
		if (value instanceof Number number) {
			return number.toString();
		}
		if (value instanceof List<?> list) {
			StringBuilder sb = new StringBuilder("[");
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(formatValue(list.get(i)));
			}
			return sb.append(']').toString();
		}
		return quote(String.valueOf(value));
	}

	private static String formatFloat(double value) {
		if (Double.isNaN(value)) {
			return "nan";
		}
		if (Double.isInfinite(value)) {
			return (value > 0) ? "inf" : "-inf";
		}
		if (value == Math.floor(value) && Math.abs(value) < 1e15) {
			return (long) value + ".0";
		}
		return Double.toString(value);
	}

	private static String quote(String str) {
		StringBuilder sb = new StringBuilder(str.length() + 2);
		sb.append('"');
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\t' -> sb.append("\\t");
				case '\r' -> sb.append("\\r");
				case '\b' -> sb.append("\\b");
				case '\f' -> sb.append("\\f");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04X", (int) c));
					}
					else {
						sb.append(c);
					}
				}
			}
		}
		return sb.append('"').toString();
	}

}
