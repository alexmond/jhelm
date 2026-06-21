package org.alexmond.gotmpl4j.html;

/**
 * Byte-slice helpers mirroring the {@code bytes} package operations the Go
 * {@code html/template} transition machine relies on. The escaper works on UTF-8
 * {@code byte[]} (not {@code char[]}) so byte offsets stay identical to Go's, which
 * indexes raw bytes. All "set" arguments are ASCII, matching Go's usage.
 */
final class Bytes {

	private Bytes() {
	}

	/**
	 * Index of the first {@code b} at or after {@code from}, or -1 (like
	 * bytes.IndexByte).
	 */
	static int indexByte(byte[] s, int from, byte b) {
		for (int i = from; i < s.length; i++) {
			if (s[i] == b) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Index of the first byte at or after {@code from} that is one of the ASCII bytes in
	 * {@code set}, or -1 (like bytes.IndexAny with an ASCII cutset).
	 */
	static int indexAny(byte[] s, int from, String set) {
		for (int i = from; i < s.length; i++) {
			int c = s[i] & 0xff;
			if (c < 0x80 && set.indexOf(c) >= 0) {
				return i;
			}
		}
		return -1;
	}

	/** Whether any byte at or after {@code from} is in the ASCII {@code set}. */
	static boolean containsAny(byte[] s, int from, String set) {
		return indexAny(s, from, set) != -1;
	}

	/** Index of the first occurrence of {@code sub} at or after {@code from}, or -1. */
	static int index(byte[] s, int from, byte[] sub) {
		if (sub.length == 0) {
			return from;
		}
		int last = s.length - sub.length;
		for (int i = from; i <= last; i++) {
			if (regionEquals(s, i, sub)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Whether {@code s[off..off+lit.length]} equals {@code lit} exactly (bounds-checked).
	 */
	static boolean regionEquals(byte[] s, int off, byte[] lit) {
		if (off < 0 || off + lit.length > s.length) {
			return false;
		}
		for (int i = 0; i < lit.length; i++) {
			if (s[off + i] != lit[i]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether {@code s[off..off+lit.length]} equals {@code lit} ignoring ASCII case
	 * (bounds-checked), like bytes.EqualFold restricted to ASCII.
	 */
	static boolean regionEqualsFold(byte[] s, int off, byte[] lit) {
		if (off < 0 || off + lit.length > s.length) {
			return false;
		}
		for (int i = 0; i < lit.length; i++) {
			if (toLowerAscii(s[off + i]) != toLowerAscii(lit[i])) {
				return false;
			}
		}
		return true;
	}

	/** ASCII lower-cases a single byte. */
	static byte toLowerAscii(byte b) {
		if (b >= 'A' && b <= 'Z') {
			return (byte) (b + ('a' - 'A'));
		}
		return b;
	}

	/** ASCII lower-cases the bytes {@code s[from..to]} into a {@link String}. */
	static String toLowerAscii(byte[] s, int from, int to) {
		StringBuilder sb = new StringBuilder(to - from);
		for (int i = from; i < to; i++) {
			sb.append((char) (toLowerAscii(s[i]) & 0xff));
		}
		return sb.toString();
	}

	/** Encodes a {@link String} to its UTF-8 bytes. */
	static byte[] utf8(String s) {
		return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

}
