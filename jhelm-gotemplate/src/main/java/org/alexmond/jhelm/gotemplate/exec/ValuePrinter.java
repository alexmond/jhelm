package org.alexmond.jhelm.gotemplate.exec;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;

final class ValuePrinter {

	private ValuePrinter() {
	}

	static void printValue(Writer writer, Object value) throws IOException {
		if (value == null) {
			return;
		}
		if (value instanceof String s) {
			writer.write(s);
		}
		else if (value instanceof Number || value instanceof Boolean) {
			writer.write(formatScalar(value));
		}
		else if (value instanceof Collection<?> coll) {
			printCollection(writer, coll);
		}
		else if (value instanceof Map<?, ?> map) {
			printMap(writer, map);
		}
		else {
			writer.write("[object " + value.getClass().getSimpleName() + "]");
		}
	}

	private static void printCollection(Writer writer, Collection<?> coll) throws IOException {
		// Match Go's fmt.Sprint format: [item1 item2 item3]
		writer.write("[");
		boolean first = true;
		for (Object item : coll) {
			if (!first) {
				writer.write(" ");
			}
			writer.write(formatScalar(item));
			first = false;
		}
		writer.write("]");
	}

	private static void printMap(Writer writer, Map<?, ?> map) throws IOException {
		// Match Go's fmt.Sprint format: map[key1:val1 key2:val2]
		writer.write("map[");
		boolean first = true;
		for (Map.Entry<?, ?> e : map.entrySet()) {
			if (!first) {
				writer.write(" ");
			}
			writer.write(formatScalar(e.getKey()) + ":" + formatScalar(e.getValue()));
			first = false;
		}
		writer.write("]");
	}

	/**
	 * Format a value to match Go's fmt.Sprint behavior. Whole-number floating-point
	 * values are rendered without a decimal point (e.g. 1.0 → "1").
	 */
	private static String formatScalar(Object value) {
		if (value instanceof Double d && d == Math.floor(d) && !Double.isInfinite(d) && d >= Long.MIN_VALUE
				&& d <= Long.MAX_VALUE) {
			return String.valueOf(d.longValue());
		}
		if (value instanceof Float f && f == Math.floor(f) && !Float.isInfinite(f) && f >= Long.MIN_VALUE
				&& f <= Long.MAX_VALUE) {
			return String.valueOf(f.longValue());
		}
		return String.valueOf(value);
	}

}
