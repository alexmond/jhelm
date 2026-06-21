package org.alexmond.gotmpl4j.html;

import java.util.HashMap;
import java.util.Map;

import org.alexmond.gotmpl4j.html.Content.Stringified;

/**
 * JavaScript contextual escapers ported from Go {@code html/template}'s js.go:
 * {@code jsValEscaper} (a value as a JSON literal), {@code jsStrEscaper},
 * {@code jsTmplLitEscaper}, {@code jsRegexpEscaper} and the shared {@code replace}.
 */
final class JsEscapers {

	// Control characters 0x00-0x1f always escaped (precedence over the per-context
	// tables).
	private static final Map<Integer, String> LOW_UNICODE = buildLowUnicode();

	private static final Map<Integer, String> JS_STR = Map.ofEntries(Map.entry(0, "\\u0000"),
			Map.entry((int) '\t', "\\t"), Map.entry((int) '\n', "\\n"), Map.entry(0x0b, "\\u000b"),
			Map.entry((int) '\f', "\\f"), Map.entry((int) '\r', "\\r"), Map.entry((int) '"', "\\u0022"),
			Map.entry((int) '`', "\\u0060"), Map.entry((int) '&', "\\u0026"), Map.entry((int) '\'', "\\u0027"),
			Map.entry((int) '+', "\\u002b"), Map.entry((int) '/', "\\/"), Map.entry((int) '<', "\\u003c"),
			Map.entry((int) '>', "\\u003e"), Map.entry((int) '\\', "\\\\"));

	// Like JS_STR plus the JS template-literal specials $, {, }.
	private static final Map<Integer, String> JS_BQ_STR = bqStr();

	// Like JS_STR but without a backslash entry (does not over-encode existing escapes).
	private static final Map<Integer, String> JS_STR_NORM = Map.ofEntries(Map.entry(0, "\\u0000"),
			Map.entry((int) '\t', "\\t"), Map.entry((int) '\n', "\\n"), Map.entry(0x0b, "\\u000b"),
			Map.entry((int) '\f', "\\f"), Map.entry((int) '\r', "\\r"), Map.entry((int) '"', "\\u0022"),
			Map.entry((int) '&', "\\u0026"), Map.entry((int) '\'', "\\u0027"), Map.entry((int) '`', "\\u0060"),
			Map.entry((int) '+', "\\u002b"), Map.entry((int) '/', "\\/"), Map.entry((int) '<', "\\u003c"),
			Map.entry((int) '>', "\\u003e"));

	private static final Map<Integer, String> JS_REGEXP = jsRegexp();

	private JsEscapers() {
	}

	/**
	 * Escapes a value to a JS expression (a JSON literal) with no side effects or free
	 * variables, mirroring Go {@code jsValEscaper}. A {@link SafeContent} of type JS is
	 * emitted verbatim; one of type JS_STR is wrapped in quotes; anything else is
	 * rendered as Go-compatible JSON. The result is padded with spaces when it abuts an
	 * identifier so it cannot run into an adjacent keyword.
	 * @param args the value(s)
	 * @return the JS expression
	 */
	static String jsValEscaper(Object... args) {
		Object a;
		if (args.length == 1) {
			if (args[0] instanceof SafeContent sc) {
				if (sc.contentType() == ContentType.JS) {
					return sc.value();
				}
				if (sc.contentType() == ContentType.JS_STR) {
					return "\"" + sc.value() + "\"";
				}
			}
			a = args[0];
		}
		else {
			a = sprintArgs(args);
		}
		String json;
		try {
			json = JsonMarshal.marshal(a);
		}
		catch (JsonMarshal.JsonException ex) {
			return " /* " + sanitizeError(ex.getMessage()) + " */null ";
		}
		if (json.isEmpty()) {
			// In `x=y/{{.}}*z` a value marshaling to "" must not yield `x=y/*z`.
			return " null ";
		}
		boolean pad = JsLex.isJSIdentPart(json.codePointAt(0))
				|| JsLex.isJSIdentPart(json.codePointBefore(json.length()));
		String body = json.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
		return pad ? " " + body + " " : body;
	}

	// fmt.Sprint of multiple arguments (a space is inserted between two non-strings).
	private static String sprintArgs(Object[] args) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < args.length; i++) {
			if (i > 0 && !(args[i - 1] instanceof String) && !(args[i] instanceof String)) {
				sb.append(' ');
			}
			sb.append(org.alexmond.gotmpl4j.GoFmt.sprint(args[i]));
		}
		return sb.toString();
	}

	// Prevents a marshaling error message (interpolated as a JS comment) from terminating
	// the comment or the script block.
	private static String sanitizeError(String message) {
		String m = message.replaceAll("(?i)<(/?)script", "\\\\x3C$1script");
		m = m.replace("*/", "* /");
		return m.replace("<!--", "\\x3C!--");
	}

	/** Escapes for inclusion between quotes in a JS string. */
	static String jsStrEscaper(Object... args) {
		Stringified r = Content.stringify(args);
		if (r.type() == ContentType.JS_STR) {
			return replace(r.text(), JS_STR_NORM);
		}
		return replace(r.text(), JS_STR);
	}

	/** Escapes for inclusion inside a JS template literal. */
	static String jsTmplLitEscaper(Object... args) {
		Stringified r = Content.stringify(args);
		return replace(r.text(), JS_BQ_STR);
	}

	/** Escapes regexp specials so the value is matched literally in a regexp literal. */
	static String jsRegexpEscaper(Object... args) {
		Stringified r = Content.stringify(args);
		String s = replace(r.text(), JS_REGEXP);
		if (s.isEmpty()) {
			// /{{.X}}/ should not become a line comment when .X is empty.
			return "(?:)";
		}
		return s;
	}

	// replace replaces each rune of s per LOW_UNICODE (control chars) then the table,
	// also
	// escaping U+2028/U+2029.
	private static String replace(String s, Map<Integer, String> table) {
		StringBuilder b = null;
		int written = 0;
		int i = 0;
		while (i < s.length()) {
			int r = s.codePointAt(i);
			int w = Character.charCount(r);
			String repl = LOW_UNICODE.get(r);
			if (repl == null) {
				repl = table.get(r);
			}
			if (repl == null && r == 0x2028) {
				repl = "\\u2028";
			}
			if (repl == null && r == 0x2029) {
				repl = "\\u2029";
			}
			if (repl != null) {
				if (b == null) {
					b = new StringBuilder(s.length());
				}
				b.append(s, written, i).append(repl);
				written = i + w;
			}
			i += w;
		}
		if (b == null) {
			return s;
		}
		b.append(s, written, s.length());
		return b.toString();
	}

	private static Map<Integer, String> buildLowUnicode() {
		Map<Integer, String> m = new HashMap<>();
		for (int i = 0; i <= 0x1f; i++) {
			m.put(i, String.format("\\u%04x", i));
		}
		m.put((int) '\t', "\\t");
		m.put((int) '\n', "\\n");
		m.put((int) '\f', "\\f");
		m.put((int) '\r', "\\r");
		return Map.copyOf(m);
	}

	private static Map<Integer, String> bqStr() {
		Map<Integer, String> m = new HashMap<>(JS_STR);
		m.put((int) '$', "\\u0024");
		m.put((int) '{', "\\u007b");
		m.put((int) '}', "\\u007d");
		return Map.copyOf(m);
	}

	private static Map<Integer, String> jsRegexp() {
		Map<Integer, String> m = new HashMap<>();
		m.put(0, "\\u0000");
		m.put((int) '\t', "\\t");
		m.put((int) '\n', "\\n");
		m.put(0x0b, "\\u000b");
		m.put((int) '\f', "\\f");
		m.put((int) '\r', "\\r");
		m.put((int) '"', "\\u0022");
		m.put((int) '$', "\\$");
		m.put((int) '&', "\\u0026");
		m.put((int) '\'', "\\u0027");
		m.put((int) '(', "\\(");
		m.put((int) ')', "\\)");
		m.put((int) '*', "\\*");
		m.put((int) '+', "\\u002b");
		m.put((int) '-', "\\-");
		m.put((int) '.', "\\.");
		m.put((int) '/', "\\/");
		m.put((int) '<', "\\u003c");
		m.put((int) '>', "\\u003e");
		m.put((int) '?', "\\?");
		m.put((int) '[', "\\[");
		m.put((int) '\\', "\\\\");
		m.put((int) ']', "\\]");
		m.put((int) '^', "\\^");
		m.put((int) '{', "\\{");
		m.put((int) '|', "\\|");
		m.put((int) '}', "\\}");
		return Map.copyOf(m);
	}

}
