package org.alexmond.gotmpl4j.html;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.TreeSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance suite for the HTML context-transition machine, ported from Go html/template
 * escape_test.go {@code TestEscapeText}. Each case runs a run of literal text through
 * {@link Escaper#escapeTextForTest} starting from the zero context and asserts the
 * resulting {@link Context}'s state/attr/delim/urlPart/element match Go's.
 *
 * <p>
 * Cases are stored as a base64-encoded TSV: {@code base64(input) state attr delim urlPart
 * element}. This is the lowest-level html conformance net — it pins the byte-by-byte
 * parser state machine that the contextual auto-escaper relies on.
 */
class EscapeTextConformanceTest {

	@Test
	void escapeTextConformance() throws Exception {
		Set<String> failures = new TreeSet<>();
		int total = 0;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				EscapeTextConformanceTest.class.getResourceAsStream("/conformance/escape_text_cases.tsv"),
				StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] p = line.split("\t", 6);
				String input = new String(Base64.getDecoder().decode(p[0]), StandardCharsets.UTF_8);
				total++;
				Context got;
				try {
					got = Escaper.escapeTextForTest(new Context(), input);
				}
				catch (Exception ex) {
					failures
						.add(String.format("[%s] threw %s: %s", input, ex.getClass().getSimpleName(), ex.getMessage()));
					continue;
				}
				String want = String.join(" ", p[1], p[2], p[3], p[4], p[5]);
				String actual = String.join(" ", got.state.name(), got.attr.name(), got.delim.name(),
						got.urlPart.name(), got.element.name());
				if (!want.equals(actual)) {
					failures.add(String.format("[%s] want=[%s] got=[%s]", input, want, actual));
				}
			}
		}
		int count = total;
		assertTrue(count > 100, "expected the full transition table; got only " + count + " cases");
		assertTrue(failures.isEmpty(), () -> "HTML transition-machine divergences from Go TestEscapeText ("
				+ failures.size() + "/" + count + "):\n" + String.join("\n", failures));
	}

}
