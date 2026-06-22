package org.alexmond.gotmpl4j.html;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

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

}
