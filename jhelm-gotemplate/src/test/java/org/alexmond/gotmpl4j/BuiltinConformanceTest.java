package org.alexmond.gotmpl4j;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance for two more Go text/template exec_test.go tables: {@code TestJSEscaping}
 * (the {@code js} builtin) and the portable subset of {@code TestIsTrue} (truthiness).
 *
 * <p>
 * TestIsTrue cases using Go-only kinds (uint8, complex64, channels, unsafe.Pointer, fixed
 * arrays) are omitted — the JVM has no equivalent; the value-bearing kinds (int, float,
 * bool, slice, map, nil, non-nil pointer) are covered.
 */
class BuiltinConformanceTest {

	private String js(String literal) throws Exception {
		GoTemplate t = new GoTemplate();
		// Pass the raw string through a backtick literal so no parser unescaping occurs.
		t.parse("p", "{{js `" + literal + "`}}");
		return t.render("p", Map.of());
	}

	@Test
	void jsEscaping() throws Exception {
		// Go JSEscapeString: escapes ' " \ and the HTML-sensitive < > & = to \\uXXXX,
		// escapes unprintables, and passes printable Unicode through unchanged.
		assertEquals("a", js("a"));
		assertEquals("\\'foo", js("'foo"));
		assertEquals("Go \\\"jump\\\" \\\\", js("Go \"jump\" \\"));
		assertEquals("Yukihiro says \\\"今日は世界\\\"", js("Yukihiro says \"今日は世界\""));
		assertEquals("unprintable \\uFFFE", js("unprintable ￾"));
		assertEquals("\\u003Chtml\\u003E", js("<html>"));
		assertEquals("no \\u003D in attributes", js("no = in attributes"));
		assertEquals("\\u0026#x27; does not become HTML entity", js("&#x27; does not become HTML entity"));
	}

	@Test
	void isTrue() {
		// int / float / bool
		assertTrue(Functions.isTrue(1L));
		assertFalse(Functions.isTrue(0L));
		assertTrue(Functions.isTrue(1.0));
		assertFalse(Functions.isTrue(0.0));
		assertTrue(Functions.isTrue(true));
		assertFalse(Functions.isTrue(false));
		// slice (non-empty / empty)
		assertTrue(Functions.isTrue(List.of(1L, 2L)));
		assertFalse(Functions.isTrue(List.of()));
		// map (non-empty / empty)
		assertTrue(Functions.isTrue(Map.of("a", 1L, "b", 2L)));
		assertFalse(Functions.isTrue(Map.of()));
		// nil
		assertFalse(Functions.isTrue(null));
		// non-nil object (Go: new(int) is true even though it points at zero)
		assertTrue(Functions.isTrue(new Object()));
	}

}
