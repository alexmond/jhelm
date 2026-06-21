package org.alexmond.gotmpl4j.html;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitionsTest {

	// Drives the transition machine across the whole input, as the escape pass does, and
	// returns the resulting context. (Raw transitions, so this does not force the RCDATA
	// scanning the escape pass applies inside <script>/<style> bodies.)
	private static Context run(String html) {
		Context c = new Context();
		byte[] s = html.getBytes(StandardCharsets.UTF_8);
		int pos = 0;
		while (pos < s.length) {
			byte[] sub = Arrays.copyOfRange(s, pos, s.length);
			Transitions.Result r = Transitions.transition(c, sub);
			if (r.consumed() == 0) {
				break;
			}
			c = r.context();
			pos += r.consumed();
		}
		return c;
	}

	@Test
	void plainTextStaysText() {
		assertEquals(State.TEXT, run("").state);
		assertEquals(State.TEXT, run("Hello world").state);
		assertEquals(State.TEXT, run("<p>Hello</p>").state);
	}

	@Test
	void insideTag() {
		Context c = run("<a ");
		assertEquals(State.TAG, c.state);
		assertEquals(Element.NONE, c.element);
	}

	@Test
	void urlAttributeContext() {
		Context before = run("<a href=");
		assertEquals(State.BEFORE_VALUE, before.state);
		assertEquals(Attr.URL, before.attr);

		Context quoted = run("<a href=\"");
		assertEquals(State.URL, quoted.state);
		assertEquals(Delim.DOUBLE_QUOTE, quoted.delim);
		assertEquals(Attr.URL, quoted.attr);

		Context unquoted = run("<a href=");
		assertEquals(Attr.URL, unquoted.attr);
	}

	@Test
	void styleAttributeIsCss() {
		Context c = run("<p style=\"");
		assertEquals(State.CSS, c.state);
		assertEquals(Delim.DOUBLE_QUOTE, c.delim);
	}

	@Test
	void eventHandlerAttributeIsJs() {
		Context c = run("<button onclick=\"");
		assertEquals(State.JS, c.state);
	}

	@Test
	void scriptAndStyleElements() {
		assertEquals(Element.SCRIPT, run("<script").element);
		Context style = run("<style>");
		assertEquals(State.CSS, style.state);
		assertEquals(Element.STYLE, style.element);
		Context script = run("<script>");
		assertEquals(State.JS, script.state);
		assertEquals(Element.SCRIPT, script.element);
	}

	@Test
	void htmlComment() {
		assertEquals(State.HTML_CMT, run("<!-- comment ").state);
		assertEquals(State.TEXT, run("<!-- comment -->").state);
	}

	@Test
	void urlQueryPart() {
		// After the '?', the URL part becomes query/fragment.
		Context c = run("<a href=\"/path?q=");
		assertEquals(State.URL, c.state);
		assertEquals(UrlPart.QUERY_OR_FRAG, c.urlPart);
	}

	@Test
	void nextJsCtxRegexpVsDivision() {
		// A '/' after '=' or '(' starts a regexp; after an identifier/number it divides.
		assertEquals(JsCtx.REGEXP, JsLex.nextJSCtx(b("x ="), 3, JsCtx.REGEXP));
		assertEquals(JsCtx.DIV_OP, JsLex.nextJSCtx(b("x"), 1, JsCtx.REGEXP));
		assertEquals(JsCtx.DIV_OP, JsLex.nextJSCtx(b("42"), 2, JsCtx.REGEXP));
		assertEquals(JsCtx.REGEXP, JsLex.nextJSCtx(b("return"), 6, JsCtx.REGEXP));
	}

	@Test
	void attrTypeClassification() {
		assertEquals(ContentType.URL, AttrTypes.attrType("href"));
		assertEquals(ContentType.CSS, AttrTypes.attrType("style"));
		assertEquals(ContentType.JS, AttrTypes.attrType("onclick"));
		assertEquals(ContentType.SRCSET, AttrTypes.attrType("srcset"));
		assertEquals(ContentType.URL, AttrTypes.attrType("data-src"));
		assertEquals(ContentType.PLAIN, AttrTypes.attrType("class"));
	}

	@Test
	void cssHelpers() {
		assertTrue(CssLex.endsWithCSSKeyword(b("background:url"), 14, "url"));
		assertFalse(CssLex.endsWithCSSKeyword(b("curl"), 4, "url"));
		assertArrayEquals(b("AB"), CssLex.decodeCSS(b("\\41\\42")));
		assertArrayEquals(b("plain"), CssLex.decodeCSS(b("plain")));
	}

	private static byte[] b(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

}
