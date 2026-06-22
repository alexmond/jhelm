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
 * Conformance suite for the CSS and URL escapers, ported from Go html/template
 * css_test.go {@code TestCSSEscaper} and url_test.go {@code TestURLFilters} /
 * {@code TestURLNormalizer}. Each case applies the named escaper to an input and asserts
 * the output matches Go's. The CSS/URL-filter cases use Go's comprehensive
 * all-bytes-plus-Unicode input string.
 */
class EscaperConformanceTest {

	@Test
	void escaperConformance() throws Exception {
		Set<String> failures = new TreeSet<>();
		int total = 0;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				EscaperConformanceTest.class.getResourceAsStream("/conformance/escaper_cases.tsv"),
				StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] p = line.split("\t", 3);
				String fn = p[0];
				String input = decode(p[1]);
				String want = decode(p[2]);
				total++;
				String got = apply(fn, input);
				if (!want.equals(got)) {
					failures.add(fn + "(" + input + ")\n    want=" + want + "\n    got =" + got);
				}
			}
		}
		int count = total;
		assertTrue(count > 5, "expected the escaper cases; got only " + count);
		assertTrue(failures.isEmpty(), () -> "CSS/URL escaper divergences from Go (" + failures.size() + "/" + count
				+ "):\n" + String.join("\n", failures));
	}

	private static String apply(String fn, String input) {
		return switch (fn) {
			case "cssEscaper" -> CssEscapers.cssEscaper(input);
			case "urlEscaper" -> UrlEscapers.urlEscaper(input);
			case "urlNormalizer" -> UrlEscapers.urlNormalizer(input);
			default -> throw new IllegalArgumentException("unknown escaper " + fn);
		};
	}

	private static String decode(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

}
