package org.alexmond.gotmpl4j.html;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.alexmond.gotmpl4j.html.Content.Stringified;

/**
 * URL/srcset contextual escapers ported from Go {@code html/template}'s url.go:
 * {@code urlFilter}, {@code urlEscaper}, {@code urlNormalizer} and
 * {@code srcsetFilterAndEscaper}.
 */
final class UrlEscapers {

	// Bit table marking HTML whitespace and ASCII alphanumerics (from Go url.go).
	private static final int[] HTML_SPACE_AND_ALNUM = { 0x00, 0x36, 0x00, 0x00, 0x01, 0x00, 0xff, 0x03, 0xfe, 0xff,
			0xff, 0x07, 0xfe, 0xff, 0xff, 0x07 };

	private UrlEscapers() {
	}

	/**
	 * Returns its input unless it has an unsafe scheme, in which case it defangs the URL.
	 */
	static String urlFilter(Object... args) {
		Stringified r = Content.stringify(args);
		if (r.type() == ContentType.URL) {
			return r.text();
		}
		if (!isSafeUrl(r.text())) {
			return "#" + Escapers.FILTER_FAILSAFE;
		}
		return r.text();
	}

	/** Produces output that can be embedded in a URL query. */
	static String urlEscaper(Object... args) {
		return urlProcessor(false, args);
	}

	/**
	 * Normalizes URL content for a quoted string or {@code url(...)} (does not encode
	 * '&').
	 */
	static String urlNormalizer(Object... args) {
		return urlProcessor(true, args);
	}

	/** Filters and normalizes a comma-separated srcset value. */
	static String srcsetFilterAndEscaper(Object... args) {
		Stringified r = Content.stringify(args);
		String text = r.text();
		if (r.type() == ContentType.SRCSET) {
			return text;
		}
		if (r.type() == ContentType.URL) {
			StringBuilder b = new StringBuilder();
			String s = processUrlOnto(text, true, b) ? b.toString() : text;
			// Commas separate sources, so a URL's commas must be encoded.
			return s.replace(",", "%2c");
		}
		byte[] s = text.getBytes(StandardCharsets.UTF_8);
		StringBuilder b = new StringBuilder();
		int written = 0;
		for (int i = 0; i < s.length; i++) {
			if (s[i] == ',') {
				filterSrcsetElement(s, written, i, b);
				b.append(',');
				written = i + 1;
			}
		}
		filterSrcsetElement(s, written, s.length, b);
		return b.toString();
	}

	private static String urlProcessor(boolean norm, Object... args) {
		Stringified r = Content.stringify(args);
		boolean normalize = norm || r.type() == ContentType.URL;
		StringBuilder b = new StringBuilder();
		if (processUrlOnto(r.text(), normalize, b)) {
			return b.toString();
		}
		return r.text();
	}

	// Appends a normalized/escaped URL to b; reports whether the output differs from s.
	private static boolean processUrlOnto(String s, boolean norm, StringBuilder b) {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		int written = 0;
		for (int i = 0; i < bytes.length; i++) {
			int c = bytes[i] & 0xff;
			if (keepUrlByte(bytes, i, c, norm)) {
				continue;
			}
			b.append(new String(bytes, written, i - written, StandardCharsets.UTF_8));
			b.append('%').append(String.format("%02x", c));
			written = i + 1;
		}
		b.append(new String(bytes, written, bytes.length - written, StandardCharsets.UTF_8));
		return written != 0;
	}

	private static boolean keepUrlByte(byte[] bytes, int i, int c, boolean norm) {
		switch (c) {
			case '!', '#', '$', '&', '*', '+', ',', '/', ':', ';', '=', '?', '@', '[', ']' -> {
				// Sub-delims/reserved: kept when normalizing, encoded otherwise.
				return norm;
			}
			case '-', '.', '_', '~' -> {
				return true;
			}
			case '%' -> {
				// Do not re-encode a valid existing escape when normalizing.
				return norm && i + 2 < bytes.length && CssLex.isHex(bytes[i + 1]) && CssLex.isHex(bytes[i + 2]);
			}
			default -> {
				return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
			}
		}
	}

	private static boolean isSafeUrl(String s) {
		int colon = s.indexOf(':');
		if (colon >= 0) {
			String protocol = s.substring(0, colon);
			if (!protocol.contains("/")) {
				String p = protocol.toLowerCase(Locale.ROOT);
				if (!p.equals("http") && !p.equals("https") && !p.equals("mailto")) {
					return false;
				}
			}
		}
		return true;
	}

	private static void filterSrcsetElement(byte[] s, int left, int right, StringBuilder b) {
		int start = left;
		while (start < right && isHtmlSpace(s[start])) {
			start++;
		}
		int end = right;
		for (int i = start; i < right; i++) {
			if (isHtmlSpace(s[i])) {
				end = i;
				break;
			}
		}
		String url = new String(s, start, end - start, StandardCharsets.UTF_8);
		if (isSafeUrl(url)) {
			boolean metadataOk = true;
			for (int i = end; i < right; i++) {
				if (!isHtmlSpaceOrAsciiAlnum(s[i])) {
					metadataOk = false;
					break;
				}
			}
			if (metadataOk) {
				b.append(new String(s, left, start - left, StandardCharsets.UTF_8));
				processUrlOnto(url, true, b);
				b.append(new String(s, end, right - end, StandardCharsets.UTF_8));
				return;
			}
		}
		b.append('#').append(Escapers.FILTER_FAILSAFE);
	}

	private static boolean isHtmlSpace(byte b) {
		int c = b & 0xff;
		return c <= 0x20 && (HTML_SPACE_AND_ALNUM[c >> 3] & (1 << (c & 0x7))) != 0;
	}

	private static boolean isHtmlSpaceOrAsciiAlnum(byte b) {
		int c = b & 0xff;
		return c < 0x80 && (HTML_SPACE_AND_ALNUM[c >> 3] & (1 << (c & 0x7))) != 0;
	}

}
