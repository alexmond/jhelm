package org.alexmond.gotmpl4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance suite for the field-access subset of Go text/template exec_test.go (data =
 * tVal), with the Go T struct reconstructed as a nested Map. Locks in the cases gotmpl4j
 * renders byte-identically to Go (130) and pins the known divergences so a regression in
 * a passing case fails the build.
 *
 * <p>
 * Cases referencing Go-only struct features (methods, func fields, String/Error-method
 * rendering) are excluded by {@link #UNSUPPORTED} — a Map data model cannot express them;
 * exercising method invocation would need a POJO harness.
 */
class TvalConformanceTest {

	// Known divergences (each tracked by a ticket); a failure NOT in this set is a
	// regression and fails the test.
	private static final Set<String> EXPECTED_DIVERGENCES = Set.of(
			// Undefined field/key -> "" vs Go "<no value>"/"<nil>" (#431).
			"map .NO", "empty nil", "html untyped nil", "NIL", "html typed nil",
			// Go struct fmt / zero-value map index / slice-cap — not expressible with Map
			// data.
			"empty with struct", "map[NO]", "map[``]", "len(s) < indexes < cap(s)",
			// gotmpl4j has no complex number type.
			"if 1.5i", "with 1.5i", "printf complex", "bug16j",
			// exec_test-only funcs (count/zeroArgs/twoArgs/oneArg) — harness, not engine.
			"printf function", "range count", "range nil count", "bug16g", "bug16i",
			// Sprig (add) / exec_test-only (echo, makemap) funcs are not on the
			// gotmpl4j-core test classpath; parens themselves work (verified, #433
			// closed).
			"parens in pipeline", "parens: $ in paren in pipe", "parens: spaces and args",
			// #441 octal integer literal (0377) lexed as decimal.
			"octal0",
			// #438 range '=' assignment does not update the outer variable.
			"issue56490");

	// Tokens that indicate a Go-only feature gotmpl4j's Map data model cannot express.
	private static final List<String> UNSUPPORTED = List.of(".Method", ".MAdd", ".Copy", ".GetU", ".MyError",
			".TrueFalse", ".BinaryFunc", ".VariadicFunc", ".NilOKFunc", ".ErrFunc", ".PanicFunc", ".TooFew", ".TooMany",
			".InvalidReturn", ".V0", ".V1", ".V2", ".W0", ".W1", ".W2", ".Str", ".Err", ".Tmpl", ".NonEmptyInterface",
			".ComplexZero", ".UPI", ".EmptyUPI", "complex", ".unexported");

	private static Map<String, Object> tval() {
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("True", true);
		d.put("I", 17L);
		d.put("U16", 16L);
		d.put("X", "x");
		d.put("S", "xyz");
		d.put("FloatZero", 0.0);
		d.put("U", Map.of("V", "v"));
		d.put("SI", List.of(3L, 4L, 5L));
		d.put("SICap", Arrays.asList(0L, 0L, 0L, 0L, 0L));
		d.put("SIEmpty", List.of());
		d.put("SB", List.of(true, false));
		d.put("AI", List.of(3L, 4L, 5L));
		d.put("PAI", List.of(3L, 4L, 5L));
		d.put("MSI", Map.of("one", 1L, "two", 2L, "three", 3L));
		d.put("MSIone", Map.of("one", 1L));
		d.put("MSIEmpty", Map.of());
		d.put("MXI", Map.of("one", 1L));
		d.put("MII", Map.of(1L, 1L));
		d.put("MI32S", Map.of(1L, "one", 2L, "two"));
		d.put("MI64S", Map.of(2L, "i642", 3L, "i643"));
		d.put("MUI32S", Map.of(2L, "u322", 3L, "u323"));
		d.put("MUI64S", Map.of(2L, "ui642", 3L, "ui643"));
		d.put("MI8S", Map.of(2L, "i82", 3L, "i83"));
		d.put("MUI8S", Map.of(2L, "u82", 3L, "u83"));
		d.put("SMSI", List.of(Map.of("one", 1L, "two", 2L), Map.of("eleven", 11L, "twelve", 12L)));
		d.put("Empty0", null);
		d.put("Empty1", 3L);
		d.put("Empty2", "empty2");
		d.put("Empty3", List.of(7L, 8L));
		d.put("Empty4", Map.of("V", "UinEmpty"));
		d.put("PI", 23L);
		d.put("PS", "a string");
		d.put("PSI", List.of(21L, 22L, 23L));
		d.put("NIL", null);
		return d;
	}

	private static boolean unsupported(String input) {
		for (String t : UNSUPPORTED) {
			if (input.contains(t)) {
				return true;
			}
		}
		return false;
	}

	@Test
	void fieldAccessConformance() throws Exception {
		Map<String, Object> data = tval();
		Set<String> unexpected = new TreeSet<>();
		try (BufferedReader r = new BufferedReader(
				new InputStreamReader(TvalConformanceTest.class.getResourceAsStream("/conformance/tval_cases.tsv"),
						StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] p = line.split("\t", 3);
				String name = p[0];
				String input = decode(p[1]);
				String expected = decode(p[2]);
				if (unsupported(input)) {
					continue;
				}
				String got;
				try {
					GoTemplate t = new GoTemplate();
					t.parse(name, input);
					got = t.render(name, data);
				}
				catch (Exception ex) {
					got = "<<" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ">>";
				}
				if (!expected.equals(got) && !EXPECTED_DIVERGENCES.contains(name)) {
					unexpected.add(String.format("[%s] in=%s want=%s got=%s", name, input, expected, got));
				}
			}
		}
		assertTrue(unexpected.isEmpty(), () -> "unexpected text/template field-access divergences (regressions):\n"
				+ String.join("\n", unexpected) + "\n(known divergences: #431, #438, #441)");
	}

	private static String decode(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

}
