package org.alexmond.gotmpl4j.html;

import java.util.Set;

/**
 * JavaScript lexical helpers from Go {@code html/template}'s js.go that the transition
 * machine needs: {@link #nextJSCtx} (does a {@code /} start a regexp or a division?),
 * {@link #isJSIdentPart}, and {@link #isJsType}.
 */
final class JsLex {

	// JS whitespace per the \s class (used to right-trim a token run in nextJSCtx).
	private static final Set<Integer> JS_WHITESPACE = Set.of(0x0c, 0x0a, 0x0d, 0x09, 0x0b, 0x20, 0xa0, 0x1680, 0x2000,
			0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200a, 0x2028, 0x2029, 0x202f,
			0x205f, 0x3000, 0xfeff);

	// Reserved JS keywords that can precede a regular expression literal.
	private static final Set<String> REGEXP_PRECEDER_KEYWORDS = Set.of("break", "case", "continue", "delete", "do",
			"else", "finally", "in", "instanceof", "return", "throw", "try", "typeof", "void");

	private static final Set<String> JS_MIME_TYPES = Set.of("", "application/ecmascript", "application/javascript",
			"application/json", "application/ld+json", "application/x-ecmascript", "application/x-javascript", "module",
			"text/ecmascript", "text/javascript", "text/javascript1.0", "text/javascript1.1", "text/javascript1.2",
			"text/javascript1.3", "text/javascript1.4", "text/javascript1.5", "text/jscript", "text/livescript",
			"text/x-ecmascript", "text/x-javascript");

	private JsLex() {
	}

	/**
	 * Returns the context that decides whether a {@code /} following the run of tokens
	 * {@code s[0..len]} starts a regexp literal or a division operator (mirrors
	 * {@code nextJSCtx}). Assumes the run has no string/comment/regexp/division tokens.
	 * @param s the token bytes
	 * @param len the effective length of {@code s} to consider
	 * @param preceding the prior JS context (returned for an all-whitespace run)
	 * @return the resulting JS context
	 */
	static JsCtx nextJSCtx(byte[] s, int len, JsCtx preceding) {
		int n = trimRightJsWhitespace(s, len);
		if (n == 0) {
			return preceding;
		}
		int c = s[n - 1] & 0xff;
		switch (c) {
			case '+', '-' -> {
				// ++ and -- are not regexp preceders, but a single + or - is.
				int start = n - 1;
				while (start > 0 && (s[start - 1] & 0xff) == c) {
					start--;
				}
				return (((n - start) & 1) == 1) ? JsCtx.REGEXP : JsCtx.DIV_OP;
			}
			case '.' -> {
				// Handle "42." -> division.
				if (n != 1 && s[n - 2] >= '0' && s[n - 2] <= '9') {
					return JsCtx.DIV_OP;
				}
				return JsCtx.REGEXP;
			}
			case ',', '<', '>', '=', '*', '%', '&', '|', '^', '?', '!', '~', '(', '[', ':', ';', '{', '}' -> {
				return JsCtx.REGEXP;
			}
			default -> {
				// Look for a trailing IdentifierName and check it against the keyword
				// set.
				int j = n;
				while (j > 0 && isJSIdentPart(s[j - 1] & 0xff)) {
					j--;
				}
				if (REGEXP_PRECEDER_KEYWORDS
					.contains(new String(s, j, n - j, java.nio.charset.StandardCharsets.UTF_8))) {
					return JsCtx.REGEXP;
				}
			}
		}
		// A punctuator/string/identifier that precedes a division operator.
		return JsCtx.DIV_OP;
	}

	/**
	 * Whether the rune is a JS identifier part (handles every codepoint that can occur in
	 * a numeric literal or keyword).
	 * @param r the rune
	 * @return {@code true} if an identifier part
	 */
	static boolean isJSIdentPart(int r) {
		return r == '$' || (r >= '0' && r <= '9') || (r >= 'A' && r <= 'Z') || r == '_' || (r >= 'a' && r <= 'z');
	}

	/**
	 * Whether a MIME type should be treated as JavaScript (for
	 * {@code <script type=...>}).
	 * @param mimeType the raw type attribute value
	 * @return {@code true} if JavaScript
	 */
	static boolean isJsType(String mimeType) {
		String type = mimeType;
		int semi = type.indexOf(';');
		if (semi >= 0) {
			type = type.substring(0, semi);
		}
		type = type.toLowerCase(java.util.Locale.ROOT).strip();
		return JS_MIME_TYPES.contains(type);
	}

	// Rune-aware right-trim of JS whitespace; returns the new effective length.
	private static int trimRightJsWhitespace(byte[] s, int len) {
		int end = len;
		while (end > 0) {
			int start = end - 1;
			while (start > 0 && (s[start] & 0xc0) == 0x80) {
				start--;
			}
			int cp = decodeRune(s, start, end);
			if (!JS_WHITESPACE.contains(cp)) {
				break;
			}
			end = start;
		}
		return end;
	}

	private static int decodeRune(byte[] s, int start, int end) {
		int b0 = s[start] & 0xff;
		if (b0 < 0x80) {
			return b0;
		}
		if (b0 < 0xe0 && start + 1 < end) {
			return ((b0 & 0x1f) << 6) | (s[start + 1] & 0x3f);
		}
		if (b0 < 0xf0 && start + 2 < end) {
			return ((b0 & 0x0f) << 12) | ((s[start + 1] & 0x3f) << 6) | (s[start + 2] & 0x3f);
		}
		if (start + 3 < end) {
			return ((b0 & 0x07) << 18) | ((s[start + 1] & 0x3f) << 12) | ((s[start + 2] & 0x3f) << 6)
					| (s[start + 3] & 0x3f);
		}
		return b0;
	}

}
