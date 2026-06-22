package org.alexmond.gotmpl4j.sprig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import org.alexmond.gotmpl4j.GoTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance suite for the Sprig string functions, ported from Masterminds/sprig's own
 * strings_test.go (the no-variable {@code runt(tpl, expect)} cases). Each template is
 * rendered through gotmpl4j (which auto-discovers the {@link SprigFunctionProvider}) and
 * compared to Sprig's expected output. Divergences from genuinely unsupported behaviour
 * are pinned in {@link #EXPECTED}.
 */
class SprigStringsConformanceTest {

	private static final Set<String> EXPECTED = Set.of();

	@Test
	void sprigStringsConformance() throws Exception {
		Set<String> failures = new TreeSet<>();
		int total = 0;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				SprigStringsConformanceTest.class.getResourceAsStream("/conformance/sprig_strings_cases.tsv"),
				StandardCharsets.UTF_8))) {
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
				if (!want.equals(got) && !EXPECTED.contains(tpl)) {
					failures.add("[" + tpl + "] want=[" + want + "] got=[" + got + "]");
				}
			}
		}
		int count = total;
		assertTrue(count > 10, "expected the Sprig strings cases; got only " + count);
		assertTrue(failures.isEmpty(), () -> "Sprig strings divergences (" + failures.size() + "/" + count + "):\n"
				+ String.join("\n", failures));
	}

	private static String decode(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

}
