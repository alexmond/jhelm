package org.alexmond.gotmpl4j.html;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Conformance suite for the JS regexp-vs-division context heuristic, ported from Go
 * html/template js_test.go {@code TestNextJsCtx}. For each token run, {@code nextJSCtx}
 * must return the same context regardless of the preceding context (except for a blank
 * run, which is returned unchanged).
 */
class JsLexConformanceTest {

	private static JsCtx ctx(String s, JsCtx preceding) {
		byte[] b = s.getBytes(StandardCharsets.UTF_8);
		return JsLex.nextJSCtx(b, b.length, preceding);
	}

	@Test
	void nextJsCtxConformance() {
		Object[][] cases = { { JsCtx.REGEXP, ";" }, { JsCtx.REGEXP, "}" }, { JsCtx.DIV_OP, ")" }, { JsCtx.DIV_OP, "]" },
				{ JsCtx.REGEXP, "(" }, { JsCtx.REGEXP, "[" }, { JsCtx.REGEXP, "{" }, { JsCtx.REGEXP, "=" },
				{ JsCtx.REGEXP, "+=" }, { JsCtx.REGEXP, "*=" }, { JsCtx.REGEXP, "*" }, { JsCtx.REGEXP, "!" },
				{ JsCtx.REGEXP, "+" }, { JsCtx.REGEXP, "-" }, { JsCtx.DIV_OP, "--" }, { JsCtx.DIV_OP, "++" },
				{ JsCtx.DIV_OP, "x--" }, { JsCtx.REGEXP, "x---" }, { JsCtx.REGEXP, "return" },
				{ JsCtx.REGEXP, "return " }, { JsCtx.REGEXP, "return\t" }, { JsCtx.REGEXP, "return\n" },
				{ JsCtx.REGEXP, "return " }, { JsCtx.DIV_OP, "x" }, { JsCtx.DIV_OP, "x " }, { JsCtx.DIV_OP, "x\t" },
				{ JsCtx.DIV_OP, "x\n" }, { JsCtx.DIV_OP, "x " }, { JsCtx.DIV_OP, "preturn" }, { JsCtx.DIV_OP, "0" },
				{ JsCtx.DIV_OP, "0." }, { JsCtx.REGEXP, "= " } };
		for (Object[] c : cases) {
			JsCtx want = (JsCtx) c[0];
			String s = (String) c[1];
			assertEquals(want, ctx(s, JsCtx.REGEXP), () -> "nextJSCtx(" + s + ", REGEXP)");
			assertEquals(want, ctx(s, JsCtx.DIV_OP), () -> "nextJSCtx(" + s + ", DIV_OP)");
		}
		// A blank run is returned unchanged.
		assertEquals(JsCtx.REGEXP, ctx("   ", JsCtx.REGEXP));
		assertEquals(JsCtx.DIV_OP, ctx("   ", JsCtx.DIV_OP));
	}

}
