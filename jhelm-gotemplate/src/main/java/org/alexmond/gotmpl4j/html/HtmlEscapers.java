package org.alexmond.gotmpl4j.html;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.alexmond.gotmpl4j.html.Content.Stringified;

/**
 * HTML contextual escapers ported from Go {@code html/template}'s html.go: the text,
 * quoted-attribute, unquoted-attribute and RCDATA escapers, the HTML name filter, the
 * comment escaper, and {@code stripTags}.
 */
final class HtmlEscapers {

	// Runes to escape inside a quoted attribute value or text node.
	private static final Map<Integer, String> HTML_REPLACEMENT = Map.of(0, "�", (int) '"', "&#34;", (int) '&', "&amp;",
			(int) '\'', "&#39;", (int) '+', "&#43;", (int) '<', "&lt;", (int) '>', "&gt;");

	// Like HTML_REPLACEMENT but without '&', to avoid over-encoding existing entities.
	private static final Map<Integer, String> HTML_NORM_REPLACEMENT = Map.of(0, "�", (int) '"', "&#34;", (int) '\'',
			"&#39;", (int) '+', "&#43;", (int) '<', "&lt;", (int) '>', "&gt;");

	// Runes to escape inside an unquoted attribute value (HTML specials +
	// browser-derived).
	private static final Map<Integer, String> HTML_NOSPACE_REPLACEMENT = Map.ofEntries(Map.entry(0, "&#xfffd;"),
			Map.entry((int) '\t', "&#9;"), Map.entry((int) '\n', "&#10;"), Map.entry(0x0b, "&#11;"),
			Map.entry((int) '\f', "&#12;"), Map.entry((int) '\r', "&#13;"), Map.entry((int) ' ', "&#32;"),
			Map.entry((int) '"', "&#34;"), Map.entry((int) '&', "&amp;"), Map.entry((int) '\'', "&#39;"),
			Map.entry((int) '+', "&#43;"), Map.entry((int) '<', "&lt;"), Map.entry((int) '=', "&#61;"),
			Map.entry((int) '>', "&gt;"), Map.entry((int) '`', "&#96;"));

	// Like HTML_NOSPACE_REPLACEMENT but without '&'.
	private static final Map<Integer, String> HTML_NOSPACE_NORM_REPLACEMENT = Map.ofEntries(Map.entry(0, "&#xfffd;"),
			Map.entry((int) '\t', "&#9;"), Map.entry((int) '\n', "&#10;"), Map.entry(0x0b, "&#11;"),
			Map.entry((int) '\f', "&#12;"), Map.entry((int) '\r', "&#13;"), Map.entry((int) ' ', "&#32;"),
			Map.entry((int) '"', "&#34;"), Map.entry((int) '\'', "&#39;"), Map.entry((int) '+', "&#43;"),
			Map.entry((int) '<', "&lt;"), Map.entry((int) '=', "&#61;"), Map.entry((int) '>', "&gt;"),
			Map.entry((int) '`', "&#96;"));

	private HtmlEscapers() {
	}

	/** Escapes for inclusion in HTML text. */
	static String htmlEscaper(Object... args) {
		Stringified r = Content.stringify(args);
		if (r.type() == ContentType.HTML) {
			return r.text();
		}
		return htmlReplacer(r.text(), HTML_REPLACEMENT, true);
	}

	/** Escapes for inclusion in unquoted attribute values. */
	static String htmlNospaceEscaper(Object... args) {
		Stringified r = Content.stringify(args);
		if (r.text().isEmpty()) {
			return Escapers.FILTER_FAILSAFE;
		}
		if (r.type() == ContentType.HTML) {
			return htmlReplacer(stripTags(r.text()), HTML_NOSPACE_NORM_REPLACEMENT, false);
		}
		return htmlReplacer(r.text(), HTML_NOSPACE_REPLACEMENT, false);
	}

	/** Escapes for inclusion in quoted attribute values. */
	static String attrEscaper(Object... args) {
		Stringified r = Content.stringify(args);
		if (r.type() == ContentType.HTML) {
			return htmlReplacer(stripTags(r.text()), HTML_NORM_REPLACEMENT, true);
		}
		return htmlReplacer(r.text(), HTML_REPLACEMENT, true);
	}

	/** Escapes for inclusion in an RCDATA element body. */
	static String rcdataEscaper(Object... args) {
		Stringified r = Content.stringify(args);
		if (r.type() == ContentType.HTML) {
			return htmlReplacer(r.text(), HTML_NORM_REPLACEMENT, true);
		}
		return htmlReplacer(r.text(), HTML_REPLACEMENT, true);
	}

	/**
	 * Accepts valid parts of an HTML attribute/tag name, or a known-safe HTML attribute.
	 */
	static String htmlNameFilter(Object... args) {
		Stringified r = Content.stringify(args);
		if (r.type() == ContentType.HTML_ATTR) {
			return r.text();
		}
		if (r.text().isEmpty()) {
			// Avoid violating structure preservation, e.g. <input checked {{.K}}={{.V}}>.
			return Escapers.FILTER_FAILSAFE;
		}
		String s = r.text().toLowerCase(Locale.ROOT);
		if (AttrTypes.attrType(s) != ContentType.PLAIN) {
			return Escapers.FILTER_FAILSAFE;
		}
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z'))) {
				return Escapers.FILTER_FAILSAFE;
			}
		}
		return s;
	}

	/**
	 * Drops content interpolated into HTML comments (the simplest, most secure policy).
	 */
	static String commentEscaper(Object... args) {
		return "";
	}

	// htmlReplacer replaces runes per the table; when badRunes is false it also escapes
	// certain non-character runes.
	private static String htmlReplacer(String s, Map<Integer, String> table, boolean badRunes) {
		StringBuilder b = null;
		int written = 0;
		int i = 0;
		while (i < s.length()) {
			int r = s.codePointAt(i);
			int w = Character.charCount(r);
			String repl = table.get(r);
			if (repl == null && !badRunes && ((r >= 0xfdd0 && r <= 0xfdef) || (r >= 0xfff0 && r <= 0xffff))) {
				repl = String.format("&#x%x;", r);
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

	// stripTags returns only the text content of a snippet of HTML, using the transition
	// machine so attribute values and "I <3 Ponies!" are not mangled.
	private static String stripTags(String html) {
		StringBuilder b = new StringBuilder();
		byte[] s = html.getBytes(StandardCharsets.UTF_8);
		Context c = new Context();
		int i = 0;
		boolean allText = true;
		while (i != s.length) {
			if (c.delim == Delim.NONE) {
				State st = c.state;
				if (c.element != Element.NONE && !st.isInTag()) {
					st = State.RCDATA;
				}
				byte[] rest = java.util.Arrays.copyOfRange(s, i, s.length);
				Transitions.Result tr = Transitions.transitionFor(st, c, rest);
				Context d = tr.context();
				int i1 = i + tr.consumed();
				if (c.state == State.TEXT || c.state == State.RCDATA) {
					int j = i1;
					if (d.state != c.state) {
						for (int j1 = j - 1; j1 >= i; j1--) {
							if (s[j1] == '<') {
								j = j1;
								break;
							}
						}
					}
					b.append(new String(s, i, j - i, StandardCharsets.UTF_8));
				}
				else {
					allText = false;
				}
				c = d;
				i = i1;
				continue;
			}
			int idx = Bytes.indexAny(s, i, delimEnds(c.delim));
			if (idx < i) {
				break;
			}
			int i1 = idx;
			if (c.delim != Delim.SPACE_OR_TAG_END) {
				i1++;
			}
			Context next = new Context();
			next.state = State.TAG;
			next.element = c.element;
			c = next;
			i = i1;
		}
		if (allText) {
			return html;
		}
		if (c.state == State.TEXT || c.state == State.RCDATA) {
			b.append(new String(s, i, s.length - i, StandardCharsets.UTF_8));
		}
		return b.toString();
	}

	private static String delimEnds(Delim delim) {
		return switch (delim) {
			case DOUBLE_QUOTE -> "\"";
			case SINGLE_QUOTE -> "'";
			case SPACE_OR_TAG_END -> " \t\n\f\r>";
			default -> "";
		};
	}

}
