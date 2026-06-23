package org.alexmond.gotmpl4j.sprig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.alexmond.gotmpl4j.GoTemplate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance suite for the Sprig functions, ported from Masterminds/sprig's own Go test
 * suite (the no-variable {@code runt(tpl, expect)} cases per category). Each template is
 * rendered through gotmpl4j — which auto-discovers the {@link SprigFunctionProvider} —
 * and compared to Sprig's expected output. Known divergences (gotmpl4j-sprig bug backlog
 * #474, plus a few intended Helm/float edges) are listed in sprig_known_divergences.txt.
 */
class SprigConformanceTest {

	// base64 templates whose divergence from Sprig is known (gotmpl4j-sprig bug backlog
	// #474, plus a few intended Helm-semantics/float-edge cases). Loaded from the
	// resource
	// so each is documented; see sprig_known_divergences.txt.
	private static final Set<String> KNOWN = loadKnownDivergences();

	@ParameterizedTest
	@ValueSource(strings = { "strings", "numeric", "defaults", "list", "dict", "crypto", "semver", "url", "date" })
	void sprigConformance(String category) throws Exception {
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
				if (!want.equals(got) && !KNOWN.contains(p[0])) {
					failures.add("[" + tpl + "] want=[" + want + "] got=[" + got + "]");
				}
			}
		}
		int count = total;
		assertTrue(count > 0, "no Sprig " + category + " cases loaded");
		assertTrue(failures.isEmpty(), () -> "Sprig " + category + " divergences (" + failures.size() + "/" + count
				+ "):\n" + String.join("\n", failures));
	}

	// runtv conformance: cases ported from Sprig's runtv(tpl, expect, vars) tests by
	// .claude/scripts/conformance/runtv_extract.go, which renders each through the real
	// Sprig funcmap (ground-truth output) and records the vars as JSON. Here the JSON is
	// parsed back into a Map and supplied as the render root (the template's "."), so
	// gotmpl4j renders the exact same template over the exact same data Sprig did.
	@ParameterizedTest
	@ValueSource(strings = { "strings", "numeric", "defaults", "list", "dict", "crypto", "date" })
	void sprigRuntvConformance(String category) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		Set<String> failures = new TreeSet<>();
		int total = 0;
		String resource = "/conformance/sprig_" + category + "_runtv_cases.tsv";
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				SprigConformanceTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] p = line.split("\t", 3);
				String tpl = decode(p[0]);
				String want = decode(p[1]);
				String json = (p.length > 2) ? decode(p[2]) : "{}";
				total++;
				String got;
				try {
					@SuppressWarnings("unchecked")
					Map<String, Object> data = mapper.readValue(json, Map.class);
					GoTemplate t = new GoTemplate();
					t.parse("r" + total, tpl);
					StringWriter w = new StringWriter();
					t.execute("r" + total, data, w);
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
		assertTrue(count > 0, "no Sprig " + category + " runtv cases loaded");
		assertTrue(failures.isEmpty(), () -> "Sprig " + category + " runtv divergences (" + failures.size() + "/"
				+ count + "):\n" + String.join("\n", failures));
	}

	private static String decode(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

	private static Set<String> loadKnownDivergences() {
		Set<String> known = new HashSet<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				SprigConformanceTest.class.getResourceAsStream("/conformance/sprig_known_divergences.txt"),
				StandardCharsets.UTF_8))) {
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
			throw new IllegalStateException("cannot load sprig_known_divergences.txt", ex);
		}
		return known;
	}

}
