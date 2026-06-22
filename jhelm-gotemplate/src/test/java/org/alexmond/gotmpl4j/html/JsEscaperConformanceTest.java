package org.alexmond.gotmpl4j.html;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Conformance suite for the JavaScript escapers, ported from Go html/template js_test.go
 * {@code TestJSStrEscaper} and {@code TestJSRegexpEscaper}. Pure string-to-string
 * escaping; the 2 invalid-UTF-8 byte-sequence cases are omitted (Go-specific byte-level
 * behaviour, not expressible with a JVM {@code String}).
 */
class JsEscaperConformanceTest {

	@Test
	void jsStrEscaper() {
		String[][] cases = { { "", "" }, { "foo", "foo" }, { "\u0000", "\\u0000" }, { "\t", "\\t" }, { "\n", "\\n" },
				{ "\r", "\\r" }, { "\u2028", "\\u2028" }, { "\u2029", "\\u2029" }, { "\\", "\\\\" }, { "\\n", "\\\\n" },
				{ "foo\r\nbar", "foo\\r\\nbar" }, { "\"", "\\u0022" }, { "'", "\\u0027" }, { "&amp;", "\\u0026amp;" },
				{ "</script>", "\\u003c\\/script\\u003e" }, { "<![CDATA[", "\\u003c![CDATA[" }, { "]]>", "]]\\u003e" },
				{ "<!--", "\\u003c!--" }, { "-->", "--\\u003e" }, { "+ADw-script+AD4-alert(1)+ADw-/script+AD4-",
						"\\u002bADw-script\\u002bAD4-alert(1)\\u002bADw-\\/script\\u002bAD4-" } };
		for (String[] c : cases) {
			assertEquals(c[1], JsEscapers.jsStrEscaper(c[0]), () -> "jsStrEscaper(" + c[0] + ")");
		}
	}

	@Test
	void jsRegexpEscaper() {
		String[][] cases = { { "", "(?:)" }, { "foo", "foo" }, { "\u0000", "\\u0000" }, { "\t", "\\t" },
				{ "\n", "\\n" }, { "\r", "\\r" }, { "\u2028", "\\u2028" }, { "\u2029", "\\u2029" }, { "\\", "\\\\" },
				{ "\\n", "\\\\n" }, { "foo\r\nbar", "foo\\r\\nbar" }, { "\"", "\\u0022" }, { "'", "\\u0027" },
				{ "&amp;", "\\u0026amp;" }, { "</script>", "\\u003c\\/script\\u003e" },
				{ "<![CDATA[", "\\u003c!\\[CDATA\\[" }, { "]]>", "\\]\\]\\u003e" }, { "<!--", "\\u003c!\\-\\-" },
				{ "-->", "\\-\\-\\u003e" }, { "*", "\\*" }, { "+", "\\u002b" }, { "?", "\\?" },
				{ "[](){}", "\\[\\]\\(\\)\\{\\}" }, { "$foo|x.y", "\\$foo\\|x\\.y" }, { "x^y", "x\\^y" } };
		for (String[] c : cases) {
			assertEquals(c[1], JsEscapers.jsRegexpEscaper(c[0]), () -> "jsRegexpEscaper(" + c[0] + ")");
		}
	}

}
