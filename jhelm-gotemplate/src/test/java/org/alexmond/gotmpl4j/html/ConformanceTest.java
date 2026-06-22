package org.alexmond.gotmpl4j.html;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.Set;

import org.junit.jupiter.api.Test;

import org.alexmond.gotmpl4j.GoTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance suite ported from Go {@code html/template}'s escape_test.go
 * {@code TestEscape} table. Each case is rendered through gotmpl4j's opt-in HTML mode and
 * compared to the exact output Go produces.
 */
class ConformanceTest {

	private static Map<String, Object> data() {
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("F", false);
		d.put("T", true);
		d.put("C", "<Cincinnati>");
		d.put("G", "<Goodbye>");
		d.put("H", "<Hello>");
		d.put("I", "${ asd `` }");
		d.put("A", List.of("<a>", "<b>"));
		d.put("E", List.of());
		d.put("N", 42);
		d.put("U", null);
		d.put("Z", null);
		d.put("W", SafeContent.html("&iexcl;<b class=\"foo\">Hello</b>, <textarea>O'World</textarea>!"));
		return d;
	}

	private static List<Case> load() throws Exception {
		List<Case> cases = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				ConformanceTest.class.getResourceAsStream("/html/escape_cases.tsv"), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] parts = line.split("\t", 3);
				cases.add(new Case(parts[0], decode(parts[1]), decode(parts[2])));
			}
		}
		return cases;
	}

	private static String decode(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

	// Cases that depend on Go's typed/untyped-nil rendering (a nil value renders as
	// "<nil>"/"null"). gotmpl4j renders a null value as empty, matching its
	// text-mode/Helm
	// behaviour; changing that for these edge cases would risk the Helm byte-parity.
	private static final Set<String> SKIP = Set.of("typedNilValue", "jsNilValueTyped", "jsNilValueUntyped");

	@Test
	void escapeConformance() throws Exception {
		Map<String, Object> data = data();
		List<String> failures = new ArrayList<>();
		for (Case c : load()) {
			if (SKIP.contains(c.name())) {
				continue;
			}
			String got;
			try {
				GoTemplate t = GoTemplate.builder().htmlEscaping().build();
				t.parse(c.name(), c.input());
				got = t.render(c.name(), data);
			}
			catch (Exception | StackOverflowError ex) {
				got = "<<threw " + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ">>";
			}
			if (!c.expected().equals(got)) {
				failures.add(String.format("[%s]%n  in:   %s%n  want: %s%n  got:  %s", c.name(), c.input(),
						c.expected(), got));
			}
		}
		assertTrue(failures.isEmpty(),
				() -> "html/template conformance failures (" + failures.size() + "):\n" + String.join("\n", failures));
	}

	private record Case(String name, String input, String expected) {
	}

}
