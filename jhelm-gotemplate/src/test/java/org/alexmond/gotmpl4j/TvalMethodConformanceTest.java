package org.alexmond.gotmpl4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance suite for the <em>method-invocation</em> subset of Go text/template
 * exec_test.go (data = tVal), reconstructing Go's {@code T}/{@code U} structs as real
 * Java objects so gotmpl4j's reflection path (no-arg methods, methods with arguments,
 * public fields, chained/piped method calls) is exercised — the cases the Map-based
 * {@link TvalConformanceTest} cannot express.
 *
 * <p>
 * Genuinely Go-only cases (nil-pointer-receiver {@code String()}/{@code Error()}
 * rendering, complex numbers, func-typed struct fields invoked via {@code call}, the
 * exec_test-only {@code vfunc}) cannot be expressed on the JVM and are pinned in
 * {@link #EXPECTED_DIVERGENCES} with a reason; a failure outside that set is a
 * regression.
 */
class TvalMethodConformanceTest {

	// Go-only cases that cannot be expressed on the JVM, each with a reason.
	private static final Set<String> EXPECTED_DIVERGENCES = Set.of(
			// Pointer-receiver String()/Error(), incl. nil receiver — no JVM equivalent.
			"V{6666}.String()", "&V{7777}.String()", "(*V)(nil).String()", "W{888}.Error()", "&W{999}.Error()",
			"(*W)(nil).Error()",
			// No complex number type.
			"if 0.0i", "with 0.0i", "printf lots",
			// *uint pointer truthiness — not modelled.
			"if UPI", "if EmptyUPI",
			// func-typed struct fields invoked via `call` — not yet supported.
			".BinaryFunc", ".VariadicFunc0", ".VariadicFunc2", ".VariadicFuncInt", "if .BinaryFunc call",
			"if not .BinaryFunc call", ".ErrFunc", "empty call after pipe valid", "pipeline func", ".NilOKFunc not nil",
			".NilOKFunc nil", "nil pipeline", "nil call arg",
			// typed-nil interface dispatch (call method on nil concrete receiver).
			"method on typed nil interface value", "if on typed nil interface value",
			"with on typed nil interface value",
			// exec_test-only `vfunc` function (harness, not engine).
			"bug6a", "bug6b", "bug6c", "bug6d",
			// Go (bool,error) two-result method — only the no-error case is modelled.
			"error method, no error");

	// Same token set the Map harness skips; here we RUN them against the POJO instead.
	private static final List<String> METHOD_TOKENS = List.of(".Method", ".MAdd", ".Copy", ".GetU", ".MyError",
			".TrueFalse", ".BinaryFunc", ".VariadicFunc", ".NilOKFunc", ".ErrFunc", ".PanicFunc", ".TooFew", ".TooMany",
			".InvalidReturn", ".V0", ".V1", ".V2", ".W0", ".W1", ".W2", ".Str", ".Err", ".Tmpl", ".NonEmptyInterface",
			".ComplexZero", ".UPI", ".EmptyUPI", "complex", ".unexported");

	private static T tval() {
		T t = new T();
		t.NonEmptyInterface = t;
		return t;
	}

	private static boolean isMethodCase(String input) {
		for (String token : METHOD_TOKENS) {
			if (input.contains(token)) {
				return true;
			}
		}
		return false;
	}

	@Test
	void methodAccessConformance() throws Exception {
		T data = tval();
		Set<String> unexpected = new TreeSet<>();
		Map<String, Boolean> ran = new LinkedHashMap<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				TvalMethodConformanceTest.class.getResourceAsStream("/conformance/tval_cases.tsv"),
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
				if (!isMethodCase(input)) {
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
				boolean ok = expected.equals(got);
				ran.put(name, ok);
				if (!ok && !EXPECTED_DIVERGENCES.contains(name)) {
					unexpected.add(String.format("[%s] in=%s want=%s got=%s", name, input, expected, got));
				}
			}
		}
		long passed = ran.values().stream().filter(Boolean::booleanValue).count();
		assertTrue(passed > 0, "no method cases were exercised — resource missing?");
		assertTrue(unexpected.isEmpty(),
				() -> "unexpected text/template method-invocation divergences (regressions):\n"
						+ String.join("\n", unexpected) + "\n(" + passed + "/" + ran.size()
						+ " method cases pass; Go-only gaps pinned in EXPECTED_DIVERGENCES)");
	}

	private static String decode(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

	/** Go's exec_test {@code U} struct: a field plus a method taking an argument. */
	public static class U {

		public String V = "v";

		public String TrueFalse(boolean x) {
			return x ? "true" : "";
		}

	}

	/**
	 * Go's exec_test {@code T} struct, restricted to the method/field surface under test.
	 */
	public static class T {

		public boolean True = true;

		public long I = 17L;

		public long U16 = 16L;

		public String X = "x";

		public String S = "xyz";

		public U U = new U();

		public List<Long> SI = List.of(3L, 4L, 5L);

		public List<Boolean> SB = List.of(true, false);

		public Map<String, Long> MSIone = Map.of("one", 1L);

		public Map<String, Long> MXI = Map.of("one", 1L);

		public Object Empty0;

		public long PI = 23L;

		// Go renders these via fmt.Stringer/error; a plain String yields the same bytes.
		public String Str = "foozle";

		public String Err = "erroozle";

		// Interface holding a *T — modelled as a back-reference to the same object.
		public T NonEmptyInterface;

		public List<String> NonEmptyInterfacePtS = List.of("a", "b");

		public String Method0() {
			return "M0";
		}

		public long Method1(long a) {
			return a;
		}

		public String Method2(long a, String b) {
			return "Method2: " + a + " " + b;
		}

		public String Method3(Object v) {
			return "Method3: " + ((v != null) ? v : "<nil>");
		}

		public U GetU() {
			return this.U;
		}

		public T Copy() {
			return this;
		}

		/** Go: adds {@code a} to each element of {@code b}. */
		public List<Long> MAdd(long a, List<Long> b) {
			return b.stream().map((x) -> x + a).toList();
		}

		/** Go returns {@code (bool, error)}; the no-error path renders the bool. */
		public boolean MyError(boolean err) {
			return err;
		}

	}

}
