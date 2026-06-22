package org.alexmond.gotmpl4j.html;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HtmlUnescapeTest {

	@Test
	void noEntitiesReturnedUnchanged() {
		assertEquals("plain text", HtmlUnescape.unescape("plain text"));
	}

	@Test
	void namedEntities() {
		assertEquals("a&b", HtmlUnescape.unescape("a&amp;b"));
		assertEquals("<>\"'", HtmlUnescape.unescape("&lt;&gt;&quot;&apos;"));
	}

	@Test
	void numericEntities() {
		assertEquals("A", HtmlUnescape.unescape("&#65;"));
		assertEquals("A", HtmlUnescape.unescape("&#x41;"));
		assertEquals("'", HtmlUnescape.unescape("&#39;"));
	}

	@Test
	void unknownOrUnterminatedLeftAsIs() {
		assertEquals("a&unknownthing;b", HtmlUnescape.unescape("a&unknownthing;b"));
		assertEquals("a&b", HtmlUnescape.unescape("a&b"));
	}

}
