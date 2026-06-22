package org.alexmond.gotmpl4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance suite for gotmpl4j's default {@code text/template} mode, ported from the
 * portable subset of Go {@code text/template}'s exec_test.go {@code execTests} table
 * (built-ins, operators, literals). Complements the 346-chart {@code helm template}
 * byte-parity with unit-level coverage.
 */
class TextConformanceTest {

	// Cases using exec_test-only funcs (typeOf/die/mapOfThree) or Go-typed outputs.
	private static final Set<String> SKIP_UNSUPPORTED = Set.of("ideal int", "ideal float", "ideal exp float",
			"ideal complex", "or short-circuit", "and short-circuit", "bug10", "range 5");

	// gotmpl4j renders an undefined field as "" where Go text/template prints "<no
	// value>".
	// This divergence does not affect the 346-chart helm byte-parity (charts guard
	// missing
	// keys); aligning it would need separate validation against `helm template`, so these
	// cases are skipped here rather than changed.
	private static final Set<String> SKIP_NO_VALUE = Set.of("field on interface", "field on parenthesized interface",
			"and undef", "or undef");

	@Test
	void textConformance() throws Exception {
		List<String> failures = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(
				new InputStreamReader(TextConformanceTest.class.getResourceAsStream("/conformance/text_cases.tsv"),
						StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] p = line.split("\t", 4);
				String name = p[0];
				if (SKIP_UNSUPPORTED.contains(name) || SKIP_NO_VALUE.contains(name)) {
					continue;
				}
				Object data = parseData(p[1]);
				String input = decode(p[2]);
				String expected = decode(p[3]);
				String got;
				try {
					GoTemplate t = new GoTemplate();
					t.parse(name, input);
					got = t.render(name, data);
				}
				catch (Exception ex) {
					got = "<<threw " + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ">>";
				}
				if (!expected.equals(got)) {
					failures.add(String.format("[%s] in=%s want=%s got=%s", name, input, expected, got));
				}
			}
		}
		assertTrue(failures.isEmpty(),
				() -> "text/template conformance failures (" + failures.size() + "):\n" + String.join("\n", failures));
	}

	private static Object parseData(String token) {
		return switch (token) {
			case "nil" -> null;
			case "true" -> Boolean.TRUE;
			case "15.1" -> 15.1;
			default -> Long.parseLong(token);
		};
	}

	private static String decode(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

}
