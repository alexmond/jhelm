package org.alexmond.gotmpl4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance suite for the core engine, ported from Go's own {@code text/template}
 * exec_test.go {@code execTest} tables by
 * {@code .claude/scripts/conformance/runtv_extract.go -mode gotmpl}. That tool renders
 * each template through Go's real {@code text/template} (builtin funcmap only) and
 * records the data as JSON, so the expected output here is ground truth straight from Go.
 * Cases whose Go output is {@code <no value>} are excluded at extraction time: gotmpl4j
 * renders nil/missing as {@code ""} (Helm-style), a single documented default difference
 * rather than a per-case bug. Remaining known divergences are listed in
 * gotmpl_known_divergences.txt.
 */
class GoTemplateConformanceTest {

	private static final Set<String> KNOWN = loadKnownDivergences();

	@Test
	void execConformance() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		Set<String> failures = new TreeSet<>();
		int total = 0;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				GoTemplateConformanceTest.class.getResourceAsStream("/conformance/gotmpl_exec_cases.tsv"),
				StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] p = line.split("\t", 3);
				String tpl = decode(p[0]);
				String want = decode(p[1]);
				String json = (p.length > 2) ? decode(p[2]) : "null";
				total++;
				String got;
				try {
					// text/template roots can be any JSON value (scalar, array, map), so
					// parse to Object and hand it to the engine as the render root.
					Object data = mapper.readValue(json, Object.class);
					GoTemplate t = new GoTemplate();
					t.parse("c" + total, tpl);
					StringWriter w = new StringWriter();
					t.execute("c" + total, data, w);
					got = w.toString();
				}
				catch (Exception ex) {
					got = "<<" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ">>";
				}
				if (!want.equals(got) && !KNOWN.contains(p[0])) {
					failures.add("[" + tpl + "] data=[" + json + "] want=[" + want + "] got=[" + got + "]");
				}
			}
		}
		int count = total;
		assertTrue(count > 0, "no gotmpl exec cases loaded");
		assertTrue(failures.isEmpty(), () -> "gotmpl exec divergences (" + failures.size() + "/" + count + "):\n"
				+ String.join("\n", failures));
	}

	private static String decode(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

	private static Set<String> loadKnownDivergences() {
		Set<String> known = new HashSet<>();
		var in = GoTemplateConformanceTest.class.getResourceAsStream("/conformance/gotmpl_known_divergences.txt");
		if (in == null) {
			return known;
		}
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				String trimmed = line.strip();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) {
					continue;
				}
				int sep = trimmed.indexOf(' ');
				known.add((sep < 0) ? trimmed : trimmed.substring(0, sep));
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("cannot load gotmpl_known_divergences.txt", ex);
		}
		return known;
	}

}
