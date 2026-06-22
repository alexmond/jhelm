package org.alexmond.gotmpl4j.html;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.alexmond.gotmpl4j.GoTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance for the error-detection path of the HTML contextual escaper, ported from Go
 * html/template escape_test.go {@code TestErrors}. Non-error cases (Go {@code err==""})
 * must escape and render without throwing; error cases must be rejected. Exact messages
 * differ, so only error-vs-no-error is checked. Cases that diverge for known reasons
 * (gotmpl4j's single-context limitation, unsupported JS template-literal features) are
 * pinned in {@link #EXPECTED}.
 */
class EscapeErrorsConformanceTest {

	private static final Set<String> EXPECTED = Set.of();

	private static Map<String, Object> data() {
		Map<String, Object> item = Map.of("K", "k", "V", "v", "X", true);
		return Map.of("Cond", true, "X", true, "K", "k", "V", "v", "H", "<b>", "URL", "http://e.com/", "Items",
				List.of(item));
	}

	@Test
	void errorDetectionConformance() throws Exception {
		Map<String, Object> data = data();
		Set<String> unexpected = new TreeSet<>();
		int total = 0;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				EscapeErrorsConformanceTest.class.getResourceAsStream("/conformance/escape_errors_cases.tsv"),
				StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] p = line.split("\t", 2);
				String input = decode(p[0]);
				boolean wantError = p.length > 1 && !p[1].isEmpty();
				total++;
				boolean threw;
				try {
					GoTemplate t = GoTemplate.builder().htmlEscaping().build();
					t.parse("e" + total, input);
					t.render("e" + total, data);
					threw = false;
				}
				catch (Exception | StackOverflowError ex) {
					threw = true;
				}
				String id = total + ":" + input;
				if (threw != wantError && !EXPECTED.contains(input)) {
					unexpected.add("[" + id + "] Go-error=" + wantError + " gotmpl4j-error=" + threw);
				}
			}
		}
		int count = total;
		assertTrue(count > 30, "expected the TestErrors table; got only " + count);
		assertTrue(unexpected.isEmpty(), () -> "HTML escaper error-detection divergences from Go (" + unexpected.size()
				+ "/" + count + "):\n" + String.join("\n", unexpected));
	}

	private static String decode(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

}
