package org.alexmond.gotmpl4j.html;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.alexmond.gotmpl4j.html.Content.Stringified;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentTest {

	@Test
	void safeContentKeepsItsType() {
		assertEquals(new Stringified("<b>x</b>", ContentType.HTML), Content.stringify(SafeContent.html("<b>x</b>")));
		assertEquals(new Stringified("a {color:red}", ContentType.CSS),
				Content.stringify(SafeContent.css("a {color:red}")));
		assertEquals(new Stringified("x+y", ContentType.JS), Content.stringify(SafeContent.js("x+y")));
		assertEquals(new Stringified("foo\\nbar", ContentType.JS_STR),
				Content.stringify(SafeContent.jsStr("foo\\nbar")));
		assertEquals(new Stringified("/path?q=1", ContentType.URL), Content.stringify(SafeContent.url("/path?q=1")));
		assertEquals(new Stringified(" dir=\"ltr\"", ContentType.HTML_ATTR),
				Content.stringify(SafeContent.htmlAttr(" dir=\"ltr\"")));
		assertEquals(new Stringified("a.png 1x", ContentType.SRCSET),
				Content.stringify(SafeContent.srcset("a.png 1x")));
	}

	@Test
	void plainStringIsPlain() {
		assertEquals(new Stringified("<b>x</b>", ContentType.PLAIN), Content.stringify("<b>x</b>"));
	}

	@Test
	void numbersAndOtherTypesRenderLikeSprint() {
		assertEquals(new Stringified("42", ContentType.PLAIN), Content.stringify(42L));
		assertEquals(new Stringified("3.5", ContentType.PLAIN), Content.stringify(3.5));
		assertEquals(new Stringified("map[a:1]", ContentType.PLAIN), Content.stringify(Map.of("a", 1L)));
	}

	@Test
	void nullArgumentsAreSkipped() {
		// A lone null still yields empty (no <nil>), matching Go issue 25875.
		assertEquals(new Stringified("", ContentType.PLAIN), Content.stringify((Object) null));
		assertEquals(new Stringified("ab", ContentType.PLAIN), Content.stringify("a", null, "b"));
	}

	@Test
	void spaceInsertedBetweenNonStringOperands() {
		// fmt.Sprint inserts a space between two operands when neither is a string.
		assertEquals(new Stringified("1 2", ContentType.PLAIN), Content.stringify(1L, 2L));
		// ... but not when either side is a string.
		assertEquals(new Stringified("1x2", ContentType.PLAIN), Content.stringify(1L, "x", 2L));
	}

	@Test
	void safeContentStringifiesToItsValue() {
		assertEquals("<b>x</b>", SafeContent.html("<b>x</b>").toString());
	}

}
