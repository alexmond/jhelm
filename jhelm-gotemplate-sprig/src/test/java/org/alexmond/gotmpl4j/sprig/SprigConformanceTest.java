package org.alexmond.gotmpl4j.sprig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.alexmond.gotmpl4j.GoTemplate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance suite for the Sprig functions, ported from Masterminds/sprig's own Go test
 * suite (the no-variable {@code runt(tpl, expect)} cases per category). Each template is
 * rendered through gotmpl4j — which auto-discovers the {@link SprigFunctionProvider} —
 * and compared to Sprig's expected output. Genuinely unsupported behaviours are pinned
 * per category in {@link #EXPECTED}.
 */
class SprigConformanceTest {

	/** category -> templates whose divergence from Sprig is known and accepted. */
	private static final Map<String, Set<String>> EXPECTED = Map.of("numeric",
			// Sub-ulp float-accumulation edge: 2.4*10*4*1.2 is 115.19999999999999 (one
			// ulp
			// below the nearest double to 115.2). gotmpl4j returns that exact IEEE value
			// and
			// Java's Double.toString is correctly shortest for it; Go's strconv prints
			// the
			// adjacent 115.2. Matching it would need a Go-faithful float formatter in
			// GoFmt,
			// which the 346-chart parity depends on — not worth the risk for this edge.
			Set.of("{{ 1.2 | mulf \"2.4\" 10 \"4\"}}"));

	@ParameterizedTest
	@ValueSource(strings = { "strings", "numeric", "defaults", "list", "dict", "crypto" })
	void sprigConformance(String category) throws Exception {
		Set<String> pinned = EXPECTED.getOrDefault(category, Set.of());
		Set<String> failures = new TreeSet<>();
		int total = 0;
		String resource = "/conformance/sprig_" + category + "_cases.tsv";
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				SprigConformanceTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] p = line.split("\t", 2);
				String tpl = decode(p[0]);
				String want = decode(p[1]);
				total++;
				String got;
				try {
					GoTemplate t = new GoTemplate();
					t.parse("c" + total, tpl);
					StringWriter w = new StringWriter();
					t.execute("c" + total, new HashMap<>(), w);
					got = w.toString();
				}
				catch (Exception ex) {
					got = "<<" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ">>";
				}
				if (!want.equals(got) && !pinned.contains(tpl)) {
					failures.add("[" + tpl + "] want=[" + want + "] got=[" + got + "]");
				}
			}
		}
		int count = total;
		assertTrue(count > 0, "no Sprig " + category + " cases loaded");
		assertTrue(failures.isEmpty(), () -> "Sprig " + category + " divergences (" + failures.size() + "/" + count
				+ "):\n" + String.join("\n", failures));
	}

	private static String decode(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

}
