package org.alexmond.gotmpl4j.html;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance suite for the CSS escape decoder, ported from Go html/template css_test.go
 * {@code TestDecodeCSS}. Each case decodes CSS escapes ({@code \A}, {@code \1234},
 * {@code \000000a}, astral {@code \12345}) and asserts the result matches Go's.
 */
class CssLexConformanceTest {

	@Test
	void decodeCssConformance() throws Exception {
		Set<String> failures = new TreeSet<>();
		int total = 0;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				CssLexConformanceTest.class.getResourceAsStream("/conformance/css_decode_cases.tsv"),
				StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] p = line.split("\t", 3);
				String input = decode(p[1]);
				String want = decode(p[2]);
				total++;
				String got = new String(CssLex.decodeCSS(input.getBytes(StandardCharsets.UTF_8)),
						StandardCharsets.UTF_8);
				if (!want.equals(got)) {
					failures.add("decodeCSS(" + input + ") want=" + want + " got=" + got);
				}
			}
		}
		int count = total;
		assertTrue(count > 10, "expected the decodeCSS table; got only " + count);
		assertTrue(failures.isEmpty(), () -> "CSS decode divergences from Go (" + failures.size() + "/" + count + "):\n"
				+ String.join("\n", failures));
	}

	private static String decode(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

	/** Ported from Go css_test.go TestIsCSSNmchar — a CSS identifier character. */
	@Test
	void isCssNmcharConformance() {
		int[][] cases = { { 0, 0 }, { '0', 1 }, { '9', 1 }, { 'A', 1 }, { 'Z', 1 }, { 'a', 1 }, { 'z', 1 }, { '_', 1 },
				{ '-', 1 }, { ':', 0 }, { ';', 0 }, { ' ', 0 }, { 0x7f, 0 }, { 0x80, 1 }, { 0x1234, 1 }, { 0xd800, 0 },
				{ 0xdc00, 0 }, { 0xfffe, 0 }, { 0x10000, 1 }, { 0x110000, 0 } };
		for (int[] c : cases) {
			assertEquals(c[1] == 1, CssLex.isCSSNmchar(c[0]), () -> "isCSSNmchar(0x" + Integer.toHexString(c[0]) + ")");
		}
	}

	/**
	 * Ported from Go css_test.go TestEndsWithCSSKeyword — case-insensitive keyword
	 * suffix.
	 */
	@Test
	void endsWithCssKeywordConformance() {
		Object[][] cases = { { "", "url", false }, { "url", "url", true }, { "URL", "url", true },
				{ "Url", "url", true }, { "url", "important", false }, { "important", "important", true },
				{ "image-url", "url", false }, { "imageurl", "url", false }, { "image url", "url", true } };
		for (Object[] c : cases) {
			byte[] b = ((String) c[0]).getBytes(StandardCharsets.UTF_8);
			assertEquals(c[2], CssLex.endsWithCSSKeyword(b, b.length, (String) c[1]),
					() -> "endsWithCSSKeyword(" + c[0] + ", " + c[1] + ")");
		}
	}

}
