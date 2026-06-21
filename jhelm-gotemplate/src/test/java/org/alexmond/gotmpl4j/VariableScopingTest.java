package org.alexmond.gotmpl4j;

import java.io.StringWriter;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Go text/template variable scoping: {@code :=} is block-scoped, {@code =} persists. */
class VariableScopingTest {

	private String render(String t) throws Exception {
		return render(t, Map.of());
	}

	private String render(String t, Map<String, Object> data) throws Exception {
		GoTemplate tmpl = new GoTemplate();
		tmpl.parse("p", t);
		StringWriter w = new StringWriter();
		tmpl.execute(data, w);
		return w.toString();
	}

	@Test
	void declareInIfBlockDoesNotLeakToOuter() throws Exception {
		// `:=` inside {{if}} is scoped to the block; the outer $x is restored after
		// {{end}}.
		assertEquals("inner=inner after=outer", render(
				"{{- $x := \"outer\" -}}{{ if true }}{{ $x := \"inner\" }}inner={{ $x }} {{ end }}after={{ $x }}"));
	}

	@Test
	void assignInIfBlockPersists() throws Exception {
		// `=` updates the existing variable and persists past the block (accumulator
		// pattern).
		assertEquals("after=set", render("{{- $x := \"\" -}}{{ if true }}{{ $x = \"set\" }}{{ end }}after={{ $x }}"));
	}

	@Test
	void declareInRangeDoesNotLeakPastRange() throws Exception {
		// `:=` inside a range body must not leak to a read after the range.
		assertEquals("[ab]outer",
				render("{{- $x := \"outer\" -}}[{{ range $i := .items }}{{ $x := $i }}{{ $x }}{{ end }}]{{ $x }}",
						Map.of("items", java.util.List.of("a", "b"))));
	}

	@Test
	void nestedDeclareShadowsThenRestores() throws Exception {
		assertEquals("a b a",
				render("{{- $x := \"a\" -}}{{ $x }}{{ if true }} {{ $x := \"b\" }}{{ $x }}{{ end }} {{ $x }}"));
	}

}
