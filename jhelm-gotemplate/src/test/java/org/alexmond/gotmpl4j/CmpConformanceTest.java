package org.alexmond.gotmpl4j;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Conformance suite for the comparison builtins (eq/ne/lt/le/gt/ge) ported from Go
 * text/template exec_test.go {@code cmpTests} (TestComparison). Covers the cases a Map
 * data model can express: numeric/string/bool literals, multi-argument {@code eq}, and
 * signed/unsigned value comparisons.
 *
 * <p>
 * Skipped as inherently Go-only: complex numbers, pointer/interface identity, and
 * uncomparable-map reflect semantics. Three Go type-strictness <em>errors</em> are
 * instead Helm-lenient in jhelm (Helm loads numbers as float64 and does not error on
 * mixed-type comparison) — exercised in {@link #helmLenientComparisons()} with the Go
 * behavior noted.
 */
class CmpConformanceTest {

	private static Map<String, Object> data() {
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("Uthree", 3L);
		d.put("Three", 3L);
		d.put("Ufour", 4L);
		d.put("NegOne", -1L);
		return d;
	}

	private String render(String expr, Map<String, Object> data) {
		try {
			GoTemplate t = new GoTemplate();
			t.parse("p", "{{" + expr + "}}");
			return t.render("p", data);
		}
		catch (Exception ex) {
			return "<<ERR>>";
		}
	}

	@Test
	void comparisonConformance() {
		Map<String, Object> d = data();
		String[][] cases = {
				// eq / ne — bool, float, int, string
				{ "eq true true", "true" }, { "eq true false", "false" }, { "eq 1.5 1.5", "true" },
				{ "eq 1.5 2.5", "false" }, { "eq 1 1", "true" }, { "eq 1 2", "false" }, { "eq `xy` `xy`", "true" },
				{ "eq `xy` `xyz`", "false" }, { "ne true true", "false" }, { "ne true false", "true" },
				{ "ne 1.5 1.5", "false" }, { "ne 1.5 2.5", "true" }, { "ne 1 1", "false" }, { "ne 1 2", "true" },
				{ "ne `xy` `xy`", "false" }, { "ne `xy` `xyz`", "true" },
				// Multi-argument eq: first equals ANY of the rest.
				{ "eq 3 4 5 6 3", "true" }, { "eq 3 4 5 6 7", "false" },
				// lt / le / gt / ge — float, int, string
				{ "lt 1.5 1.5", "false" }, { "lt 1.5 2.5", "true" }, { "lt 1 1", "false" }, { "lt 1 2", "true" },
				{ "lt `xy` `xy`", "false" }, { "lt `xy` `xyz`", "true" }, { "le 1.5 1.5", "true" },
				{ "le 1.5 2.5", "true" }, { "le 2.5 1.5", "false" }, { "le 1 1", "true" }, { "le 1 2", "true" },
				{ "le 2 1", "false" }, { "le `xy` `xy`", "true" }, { "le `xy` `xyz`", "true" },
				{ "le `xyz` `xy`", "false" }, { "gt 1.5 1.5", "false" }, { "gt 1.5 2.5", "false" },
				{ "gt 1 1", "false" }, { "gt 2 1", "true" }, { "gt 1 2", "false" }, { "gt `xy` `xy`", "false" },
				{ "gt `xy` `xyz`", "false" }, { "ge 1.5 1.5", "true" }, { "ge 1.5 2.5", "false" },
				{ "ge 2.5 1.5", "true" }, { "ge 1 1", "true" }, { "ge 1 2", "false" }, { "ge 2 1", "true" },
				{ "ge `xy` `xy`", "true" }, { "ge `xy` `xyz`", "false" }, { "ge `xyz` `xy`", "true" },
				// Value comparisons (signed/unsigned collapse to the same value in
				// jhelm).
				{ "eq .Uthree .Uthree", "true" }, { "eq .Uthree .Ufour", "false" }, { "eq .Uthree .Three", "true" },
				{ "eq .Three .Uthree", "true" }, { "le .Uthree .Three", "true" }, { "ge .Uthree .Three", "true" },
				{ "lt .Uthree .Three", "false" }, { "gt .Uthree .Three", "false" }, { "eq .Ufour .Three", "false" },
				{ "lt .Ufour .Three", "false" }, { "gt .Ufour .Three", "true" }, { "eq .NegOne .Uthree", "false" },
				{ "ne .NegOne .Uthree", "true" }, { "lt .NegOne .Uthree", "true" }, { "le .NegOne .Uthree", "true" },
				{ "gt .NegOne .Uthree", "false" }, { "ge .NegOne .Uthree", "false" }, };
		for (String[] c : cases) {
			assertEquals(c[1], render(c[0], d), () -> "comparison: {{" + c[0] + "}}");
		}
	}

	@Test
	void helmLenientComparisons() {
		Map<String, Object> d = data();
		// Go text/template ERRORS on these (strict type matching); jhelm is Helm-lenient
		// —
		// Helm loads numbers as float64, so cross-int/float comparison is value-based and
		// mixed-type eq returns a boolean instead of failing the render. Documented, not
		// a
		// bug (cf. #431 missingkey).
		assertEquals("true", render("eq 2 2.0", d), "Go errors (int vs float); jhelm coerces numerically");
		assertEquals("false", render("eq `xy` 1", d), "Go errors (string vs int); jhelm returns false");
		assertEquals("false", render("lt true true", d), "Go errors (bool unordered); jhelm returns false");
	}

}
