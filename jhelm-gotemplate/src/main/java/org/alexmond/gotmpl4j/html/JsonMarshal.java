package org.alexmond.gotmpl4j.html;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON marshaler matching Go {@code encoding/json}'s default
 * ({@code escapeHTML}) output over the gotmpl4j value model
 * (string/number/boolean/null/map/list). Used by {@link JsEscapers#jsValEscaper} so a
 * value interpolated into a JS value context is the same JSON Go would produce.
 *
 * <p>
 * Notable Go-isms reproduced: {@code <}, {@code >}, {@code &} in strings are escaped as
 * {@code <}/{@code >}/{@code &}; {@code U+2028}/{@code U+2029} are escaped; map keys are
 * sorted; whole floats print without a decimal point ({@code 8080.0 -> 8080}); very
 * small/large floats use exponent form.
 */
final class JsonMarshal {

	private JsonMarshal() {
	}

	/**
	 * Marshals a value to JSON like Go's {@code json.Marshal}.
	 * @param value the value
	 * @return the JSON text
	 */
	static String marshal(Object value) {
		StringBuilder b = new StringBuilder();
		write(b, value);
		return b.toString();
	}

	private static void write(StringBuilder b, Object value) {
		switch (value) {
			case null -> b.append("null");
			case String s -> writeString(b, s);
			case SafeContent sc -> writeString(b, sc.value());
			case Boolean bool -> b.append(bool ? "true" : "false");
			case Double d -> b.append(jsonFloat(d));
			case Float f -> b.append(jsonFloat(f));
			case Number n -> b.append(n.toString());
			case Map<?, ?> map -> writeMap(b, map);
			case Collection<?> coll -> writeArray(b, coll);
			default -> writeOther(b, value);
		}
	}

	private static void writeOther(StringBuilder b, Object value) {
		if (value.getClass().isArray()) {
			List<Object> list = new ArrayList<>();
			int len = java.lang.reflect.Array.getLength(value);
			for (int i = 0; i < len; i++) {
				list.add(java.lang.reflect.Array.get(value, i));
			}
			writeArray(b, list);
		}
		else {
			// Fall back to the string form (Go would use a Stringer/encoding here).
			writeString(b, String.valueOf(value));
		}
	}

	private static void writeMap(StringBuilder b, Map<?, ?> map) {
		List<String> keys = new ArrayList<>();
		for (Object k : map.keySet()) {
			keys.add(String.valueOf(k));
		}
		keys.sort(null);
		b.append('{');
		for (int i = 0; i < keys.size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			String key = keys.get(i);
			writeString(b, key);
			b.append(':');
			write(b, map.get(rawKey(map, key)));
		}
		b.append('}');
	}

	// Find the original key object equal (by string form) to the sorted string key.
	private static Object rawKey(Map<?, ?> map, String key) {
		if (map.containsKey(key)) {
			return key;
		}
		for (Object k : map.keySet()) {
			if (String.valueOf(k).equals(key)) {
				return k;
			}
		}
		return key;
	}

	private static void writeArray(StringBuilder b, Collection<?> coll) {
		b.append('[');
		boolean first = true;
		for (Object e : coll) {
			if (!first) {
				b.append(',');
			}
			write(b, e);
			first = false;
		}
		b.append(']');
	}

	private static void writeString(StringBuilder b, String s) {
		b.append('"');
		int i = 0;
		while (i < s.length()) {
			int r = s.codePointAt(i);
			int w = Character.charCount(r);
			switch (r) {
				case '"' -> b.append("\\\"");
				case '\\' -> b.append("\\\\");
				case '\n' -> b.append("\\n");
				case '\r' -> b.append("\\r");
				case '\t' -> b.append("\\t");
				case 0x2028 -> b.append("\\u2028");
				case 0x2029 -> b.append("\\u2029");
				default -> {
					if (r < 0x20 || r == '<' || r == '>' || r == '&') {
						b.append(String.format("\\u%04x", r));
					}
					else {
						b.appendCodePoint(r);
					}
				}
			}
			i += w;
		}
		b.append('"');
	}

	// Go encoding/json float64 formatting: whole/normal floats use plain decimal; very
	// small (|x| < 1e-6) or very large (|x| >= 1e21) use exponent form.
	private static String jsonFloat(double f) {
		if (Double.isNaN(f) || Double.isInfinite(f)) {
			String which = Double.isNaN(f) ? "NaN" : ((f > 0) ? "+Inf" : "-Inf");
			throw new JsonException("json: unsupported value: " + which);
		}
		if (f == 0.0) {
			return "0";
		}
		double abs = Math.abs(f);
		if (abs < 1e-6 || abs >= 1e21) {
			BigDecimal bd = new BigDecimal(Double.toString(abs)).stripTrailingZeros();
			String digits = bd.unscaledValue().toString();
			int exp = bd.precision() - bd.scale() - 1;
			String mantissa = (digits.length() == 1) ? digits : digits.charAt(0) + "." + digits.substring(1);
			String es = (exp < 0) ? "-" : "+";
			String ea = Integer.toString(Math.abs(exp));
			// Go: e+NN keeps a min of 2 exponent digits; e-N is trimmed to its minimum.
			if (es.equals("+") && ea.length() < 2) {
				ea = "0" + ea;
			}
			return ((f < 0) ? "-" : "") + mantissa + "e" + es + ea;
		}
		return new BigDecimal(Double.toString(f)).stripTrailingZeros().toPlainString();
	}

	/** Thrown for values Go {@code json.Marshal} rejects (NaN, infinities). */
	static final class JsonException extends RuntimeException {

		JsonException(String message) {
			super(message);
		}

	}

}
