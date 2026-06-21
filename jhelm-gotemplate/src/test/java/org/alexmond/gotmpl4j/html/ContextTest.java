package org.alexmond.gotmpl4j.html;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextTest {

	@Test
	void zeroValueIsStartContext() {
		Context c = new Context();
		assertEquals(State.TEXT, c.state());
		assertEquals(Delim.NONE, c.delim());
		assertEquals(UrlPart.NONE, c.urlPart);
		assertEquals(JsCtx.REGEXP, c.jsCtx);
		assertEquals(Attr.NONE, c.attr);
		assertEquals(Element.NONE, c.element);
	}

	@Test
	void eqComparesAllFields() {
		Context a = new Context();
		Context b = new Context();
		assertTrue(a.eq(b));

		b.state = State.JS;
		assertFalse(a.eq(b));
	}

	@Test
	void eqTreatsNullAndEmptyJsBraceDepthAsEqual() {
		Context a = new Context();
		Context b = new Context();
		b.jsBraceDepth = List.of();
		assertTrue(a.eq(b));

		b.jsBraceDepth = List.of(1);
		assertFalse(a.eq(b));
	}

	@Test
	void copyIsIndependent() {
		Context a = new Context();
		a.state = State.URL;
		a.jsBraceDepth = new java.util.ArrayList<>(List.of(2));

		Context b = a.copy();
		assertTrue(a.eq(b));
		assertNotSame(a.jsBraceDepth, b.jsBraceDepth);

		b.jsBraceDepth.add(3);
		assertEquals(1, a.jsBraceDepth.size());
	}

	@Test
	void mangleIsIdentityForTextAndDistinctOtherwise() {
		Context text = new Context();
		assertEquals("view", text.mangle("view"));

		Context js = new Context();
		js.state = State.JS;
		Context url = new Context();
		url.state = State.URL;
		assertFalse(js.mangle("view").equals(url.mangle("view")));
		assertTrue(js.mangle("view").startsWith("view$htmltemplate_"));
	}

	@Test
	void statePredicates() {
		assertTrue(State.HTML_CMT.isComment());
		assertFalse(State.TEXT.isComment());
		assertTrue(State.ATTR_NAME.isInTag());
		assertFalse(State.JS.isInTag());
		assertTrue(State.JS_DQ_STR.isInScriptLiteral());
		assertFalse(State.JS.isInScriptLiteral());
	}

}
