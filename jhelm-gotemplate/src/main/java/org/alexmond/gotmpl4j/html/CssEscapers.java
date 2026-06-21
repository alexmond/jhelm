package org.alexmond.gotmpl4j.html;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.alexmond.gotmpl4j.html.Content.Stringified;

/**
 * CSS contextual escapers ported from Go {@code html/template}'s css.go:
 * {@code cssEscaper} (hex-escapes HTML/CSS specials) and {@code cssValueFilter} (filters
 * unsafe CSS values).
 */
final class CssEscapers {

	private static final Map<Integer, String> CSS_REPLACEMENT = Map.ofEntries(Map.entry(0, "\\0"),
			Map.entry((int) '\t', "\\9"), Map.entry((int) '\n', "\\a"), Map.entry((int) '\f', "\\c"),
			Map.entry((int) '\r', "\\d"), Map.entry((int) '"', "\\22"), Map.entry((int) '&', "\\26"),
			Map.entry((int) '\'', "\\27"), Map.entry((int) '(', "\\28"), Map.entry((int) ')', "\\29"),
			Map.entry((int) '+', "\\2b"), Map.entry((int) '/', "\\2f"), Map.entry((int) ':', "\\3a"),
			Map.entry((int) ';', "\\3b"), Map.entry((int) '<', "\\3c"), Map.entry((int) '>', "\\3e"),
			Map.entry((int) '\\', "\\\\"), Map.entry((int) '{', "\\7b"), Map.entry((int) '}', "\\7d"));

	private CssEscapers() {
	}

	/** Escapes HTML and CSS special characters using {@code \<hex>} escapes. */
	static String cssEscaper(Object... args) {
		String s = Content.stringify(args).text();
		StringBuilder b = null;
		int written = 0;
		int i = 0;
		while (i < s.length()) {
			int r = s.codePointAt(i);
			int w = Character.charCount(r);
			String repl = CSS_REPLACEMENT.get(r);
			if (repl != null) {
				if (b == null) {
					b = new StringBuilder(s.length());
				}
				b.append(s, written, i).append(repl);
				written = i + w;
				// A trailing space terminates the hex escape so it does not absorb a
				// following hex digit or space.
				if (!"\\\\".equals(repl)
						&& (written == s.length() || isHex(s.charAt(written)) || isCssSpace(s.charAt(written)))) {
					b.append(' ');
				}
			}
			i += w;
		}
		if (b == null) {
			return s;
		}
		b.append(s, written, s.length());
		return b.toString();
	}

	/**
	 * Allows innocuous CSS values and filters out anything that could break out of
	 * context.
	 */
	static String cssValueFilter(Object... args) {
		Stringified r = Content.stringify(args);
		if (r.type() == ContentType.CSS) {
			return r.text();
		}
		byte[] b = CssLex.decodeCSS(r.text().getBytes(StandardCharsets.UTF_8));
		StringBuilder id = new StringBuilder();
		for (int i = 0; i < b.length; i++) {
			int c = b[i] & 0xff;
			switch (c) {
				case 0, '"', '\'', '(', ')', '/', ';', '@', '[', '\\', ']', '`', '{', '}', '<', '>' -> {
					return Escapers.FILTER_FAILSAFE;
				}
				case '-' -> {
					// Disallow <!-- or --> ; "--" should not appear in valid identifiers.
					if (i != 0 && (b[i - 1] & 0xff) == '-') {
						return Escapers.FILTER_FAILSAFE;
					}
				}
				default -> {
					if (c < 0x80 && CssLex.isCSSNmchar(c)) {
						id.append((char) c);
					}
				}
			}
		}
		String idStr = id.toString().toLowerCase(Locale.ROOT);
		if (idStr.contains("expression") || idStr.contains("mozbinding")) {
			return Escapers.FILTER_FAILSAFE;
		}
		return new String(b, StandardCharsets.UTF_8);
	}

	private static boolean isHex(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	private static boolean isCssSpace(char c) {
		return c == '\t' || c == '\n' || c == '\f' || c == '\r' || c == ' ';
	}

}
