package org.alexmond.gotmpl4j.html;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * CSS lexical helpers from Go {@code html/template}'s css.go that the transition machine
 * needs: {@link #endsWithCSSKeyword}, {@link #decodeCSS}, and the small predicates around
 * them.
 */
final class CssLex {

	private static final int MAX_RUNE = 0x10ffff;

	private CssLex() {
	}

	/**
	 * Whether {@code b[0..len]} ends with an ident case-insensitively matching the
	 * lower-case {@code kw} (mirrors {@code endsWithCSSKeyword}).
	 * @param b the bytes
	 * @param len the effective length to consider
	 * @param kw the lower-case keyword
	 * @return {@code true} if {@code b} ends with the keyword as a whole ident
	 */
	static boolean endsWithCSSKeyword(byte[] b, int len, String kw) {
		int i = len - kw.length();
		if (i < 0) {
			return false;
		}
		if (i != 0) {
			// The char just before must not itself be an ident char (else too long).
			int start = i - 1;
			while (start > 0 && (b[start] & 0xc0) == 0x80) {
				start--;
			}
			if (isCSSNmchar(decodeRune(b, start, i))) {
				return false;
			}
		}
		return Bytes.toLowerAscii(b, i, len).equals(kw);
	}

	/**
	 * Whether the rune is allowed anywhere in a CSS identifier (the CSS3 nmchar
	 * production, ignoring multi-rune escapes).
	 * @param r the rune
	 * @return {@code true} if a CSS nmchar
	 */
	static boolean isCSSNmchar(int r) {
		return (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || r == '-' || r == '_'
				|| (r >= 0x80 && r <= 0xd7ff) || (r >= 0xe000 && r <= 0xfffd) || (r >= 0x10000 && r <= 0x10ffff);
	}

	/** Whether the byte is a hex digit. */
	static boolean isHex(byte c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	/** Whether the byte is a CSS space char (wc). */
	static boolean isCSSSpace(byte b) {
		return switch (b) {
			case '\t', '\n', '\f', '\r', ' ' -> true;
			default -> false;
		};
	}

	/**
	 * Decodes CSS3 escapes in {@code s}, returning the input unchanged if there are none,
	 * or a new array otherwise (mirrors {@code decodeCSS}).
	 * @param s the bytes
	 * @return the decoded bytes
	 */
	static byte[] decodeCSS(byte[] s) {
		if (Bytes.indexByte(s, 0, (byte) '\\') == -1) {
			return s;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream(s.length);
		int pos = 0;
		while (pos < s.length) {
			int i = Bytes.indexByte(s, pos, (byte) '\\');
			if (i == -1) {
				i = s.length;
			}
			out.write(s, pos, i - pos);
			pos = i;
			if (s.length - pos < 2) {
				break;
			}
			if (isHex(s[pos + 1])) {
				int j = 2;
				while (pos + j < s.length && j < 7 && isHex(s[pos + j])) {
					j++;
				}
				int r = hexDecode(s, pos + 1, pos + j);
				if (r > MAX_RUNE) {
					r /= 16;
					j--;
				}
				byte[] enc = encodeRune(r);
				out.write(enc, 0, enc.length);
				pos = skipCSSSpace(s, pos + j);
			}
			else {
				int n = runeLen(s, pos + 1);
				out.write(s, pos + 1, n);
				pos = pos + 1 + n;
			}
		}
		return out.toByteArray();
	}

	private static int hexDecode(byte[] s, int from, int to) {
		int n = 0;
		for (int i = from; i < to; i++) {
			byte c = s[i];
			n <<= 4;
			if (c >= '0' && c <= '9') {
				n |= c - '0';
			}
			else if (c >= 'a' && c <= 'f') {
				n |= c - 'a' + 10;
			}
			else if (c >= 'A' && c <= 'F') {
				n |= c - 'A' + 10;
			}
		}
		return n;
	}

	private static int skipCSSSpace(byte[] c, int pos) {
		if (pos >= c.length) {
			return pos;
		}
		switch (c[pos]) {
			case '\t', '\n', '\f', ' ' -> {
				return pos + 1;
			}
			case '\r' -> {
				if (pos + 1 < c.length && c[pos + 1] == '\n') {
					return pos + 2;
				}
				return pos + 1;
			}
			default -> {
				return pos;
			}
		}
	}

	private static byte[] encodeRune(int cp) {
		return new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
	}

	private static int runeLen(byte[] s, int idx) {
		int b0 = s[idx] & 0xff;
		int len;
		if (b0 < 0x80) {
			len = 1;
		}
		else if (b0 < 0xe0) {
			len = 2;
		}
		else if (b0 < 0xf0) {
			len = 3;
		}
		else {
			len = 4;
		}
		return Math.min(len, s.length - idx);
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
