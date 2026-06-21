package org.alexmond.gotmpl4j.html;

import java.util.Map;

/**
 * Decodes HTML character references, used by the escape pass's {@code contextAfterText}
 * to decode an attribute value before applying non-HTML (JS/CSS/URL) transition rules —
 * so a token boundary written as an entity (e.g. {@code &quot;}) is seen by those rules.
 * Mirrors the relevant part of Go's {@code html.UnescapeString}: numeric references
 * (decimal and hex) plus the common named references.
 */
final class HtmlUnescape {

	private static final Map<String, String> NAMED = Map.ofEntries(Map.entry("amp", "&"), Map.entry("lt", "<"),
			Map.entry("gt", ">"), Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("nbsp", " "),
			Map.entry("#39", "'"));

	private HtmlUnescape() {
	}

	/**
	 * Decodes the HTML character references in {@code s}.
	 * @param s the possibly-encoded string
	 * @return the decoded string
	 */
	static String unescape(String s) {
		int amp = s.indexOf('&');
		if (amp < 0) {
			return s;
		}
		StringBuilder b = new StringBuilder(s.length());
		int i = 0;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c != '&') {
				b.append(c);
				i++;
				continue;
			}
			int semi = s.indexOf(';', i + 1);
			if (semi < 0 || semi - i > 32) {
				b.append(c);
				i++;
				continue;
			}
			String entity = s.substring(i + 1, semi);
			String decoded = decode(entity);
			if (decoded != null) {
				b.append(decoded);
				i = semi + 1;
			}
			else {
				b.append(c);
				i++;
			}
		}
		return b.toString();
	}

	private static String decode(String entity) {
		if (entity.startsWith("#x") || entity.startsWith("#X")) {
			return fromCodePoint(entity.substring(2), 16);
		}
		if (entity.startsWith("#")) {
			return fromCodePoint(entity.substring(1), 10);
		}
		return NAMED.get(entity);
	}

	private static String fromCodePoint(String digits, int radix) {
		if (digits.isEmpty()) {
			return null;
		}
		try {
			int cp = Integer.parseInt(digits, radix);
			if (cp < 0 || cp > 0x10ffff) {
				return null;
			}
			return new String(Character.toChars(cp));
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
