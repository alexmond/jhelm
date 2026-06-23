package org.alexmond.jhelm.gotemplate.helm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.alexmond.gotmpl4j.GoTemplate;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance suite for the Helm template functions, ported from Helm's own
 * {@code pkg/engine/funcs_test.go} {@code {tpl, expect, vars}} tables by
 * {@code .claude/scripts/conformance/runtv_extract.go -mode helm}. The {@code expect}
 * values are Helm's ground-truth outputs taken verbatim from source; the {@code vars} are
 * recorded as JSON and supplied as the render root. Each template renders through
 * gotmpl4j with the full Helm funcmap on the classpath (engine builtins + Sprig + the
 * Helm conversion functions in this module) and is compared to Helm's expected output.
 * Known divergences are listed in helm_known_divergences.txt and keyed on (template,
 * data), since the same template can pass on one input and diverge on another (e.g.
 * {@code fromYaml} on valid vs bad input).
 */
class HelmConformanceTest {

	private static final Set<String> KNOWN = loadKnownDivergences();

	@Test
	void helmFuncsConformance() throws Exception {
		JsonMapper mapper = JsonMapper.builder().build();
		Set<String> failures = new TreeSet<>();
		int total = 0;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				HelmConformanceTest.class.getResourceAsStream("/conformance/helm_funcs_cases.tsv"),
				StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] p = line.split("\t", 3);
				String key = p[0] + "\t" + ((p.length > 2) ? p[2] : "");
				String tpl = decode(p[0]);
				String want = decode(p[1]);
				String json = (p.length > 2) ? decode(p[2]) : "null";
				total++;
				String got;
				try {
					Object data = mapper.readValue(json, Object.class);
					GoTemplate t = new GoTemplate();
					t.parse("h" + total, tpl);
					StringWriter w = new StringWriter();
					t.execute("h" + total, data, w);
					got = w.toString();
				}
				catch (Exception ex) {
					got = "<<" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ">>";
				}
				if (!want.equals(got) && !KNOWN.contains(key)) {
					failures.add("[" + tpl + "] data=[" + json + "] want=[" + want + "] got=[" + got + "]");
					// Emit the (b64tpl\tb64data) key so the divergence file can be
					// regenerated.
					System.err.println("DIVERGENCE-KEY\t" + key);
				}
			}
		}
		int count = total;
		assertTrue(count > 0, "no Helm funcs cases loaded");
		assertTrue(failures.isEmpty(), () -> "Helm funcs divergences (" + failures.size() + "/" + count + "):\n"
				+ String.join("\n", failures));
	}

	private static String decode(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

	private static Set<String> loadKnownDivergences() {
		Set<String> known = new HashSet<>();
		var in = HelmConformanceTest.class.getResourceAsStream("/conformance/helm_known_divergences.txt");
		if (in == null) {
			return known;
		}
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				String trimmed = line.strip();
				if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
					known.add(trimmed);
				}
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("cannot load helm_known_divergences.txt", ex);
		}
		return known;
	}

}
