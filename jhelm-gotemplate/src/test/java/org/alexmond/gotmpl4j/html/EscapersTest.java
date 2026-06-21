package org.alexmond.gotmpl4j.html;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EscapersTest {

	@Test
	void htmlEscaperEscapesSpecialsAndHonoursSafeHtml() {
		assertEquals("&lt;b&gt;Hi &amp; bye&lt;/b&gt;", HtmlEscapers.htmlEscaper("<b>Hi & bye</b>"));
		assertEquals("O&#39;Reilly", HtmlEscapers.htmlEscaper("O'Reilly"));
		// A SafeContent.html value passes through unescaped.
		assertEquals("<b>bold</b>", HtmlEscapers.htmlEscaper(SafeContent.html("<b>bold</b>")));
	}

	@Test
	void attrAndNospaceEscapers() {
		assertEquals("a&#43;b", HtmlEscapers.attrEscaper("a+b"));
		assertEquals("a&#32;b", HtmlEscapers.htmlNospaceEscaper("a b"));
		assertEquals(Escapers.FILTER_FAILSAFE, HtmlEscapers.htmlNospaceEscaper(""));
	}

	@Test
	void htmlNameFilter() {
		assertEquals("class", HtmlEscapers.htmlNameFilter("class"));
		// onclick is an event-handler attribute (script), so it is filtered out.
		assertEquals(Escapers.FILTER_FAILSAFE, HtmlEscapers.htmlNameFilter("onclick"));
		assertEquals(Escapers.FILTER_FAILSAFE, HtmlEscapers.htmlNameFilter("a b"));
	}

	@Test
	void commentEscaperDropsContent() {
		assertEquals("", HtmlEscapers.commentEscaper("anything at all"));
	}

	@Test
	void jsStrEscaper() {
		assertEquals("O\\u0027Reilly", JsEscapers.jsStrEscaper("O'Reilly"));
		assertEquals("a\\u003cb", JsEscapers.jsStrEscaper("a<b"));
		assertEquals("line1\\nline2", JsEscapers.jsStrEscaper("line1\nline2"));
	}

	@Test
	void jsRegexpEscaper() {
		assertEquals("foo\\.bar", JsEscapers.jsRegexpEscaper("foo.bar"));
		assertEquals("(?:)", JsEscapers.jsRegexpEscaper(""));
	}

	@Test
	void cssEscaper() {
		// '(' -> \28 (no trailing space: next char 'x' is not a hex digit); ')' -> \29
		// (trailing space terminates the escape at end of input).
		assertEquals("url\\28x\\29 ", CssEscapers.cssEscaper("url(x)"));
		assertEquals("a\\3a b", CssEscapers.cssEscaper("a:b"));
	}

	@Test
	void cssValueFilter() {
		assertEquals("12px", CssEscapers.cssValueFilter("12px"));
		assertEquals(Escapers.FILTER_FAILSAFE, CssEscapers.cssValueFilter("expression(alert(1))"));
		assertEquals(Escapers.FILTER_FAILSAFE, CssEscapers.cssValueFilter("foo;bar"));
	}

	@Test
	void urlFilterBlocksUnsafeSchemes() {
		assertEquals("/safe/path", UrlEscapers.urlFilter("/safe/path"));
		assertEquals("http://example.com", UrlEscapers.urlFilter("http://example.com"));
		assertEquals("#" + Escapers.FILTER_FAILSAFE, UrlEscapers.urlFilter("javascript:alert(1)"));
	}

	@Test
	void urlEscaperAndNormalizer() {
		assertEquals("a%3db%26c", UrlEscapers.urlEscaper("a=b&c"));
		// The normalizer keeps reserved chars but encodes spaces.
		assertEquals("/a/b?x=1%20", UrlEscapers.urlNormalizer("/a/b?x=1 "));
	}

	@Test
	void srcsetFilterAndEscaper() {
		assertEquals("/img.png 1x", UrlEscapers.srcsetFilterAndEscaper("/img.png 1x"));
		// A javascript: URL in a srcset element is defanged.
		assertEquals("#" + Escapers.FILTER_FAILSAFE, UrlEscapers.srcsetFilterAndEscaper("javascript:alert(1) 1x"));
	}

	@Test
	void registryExposesGoNames() {
		assertEquals(15, Escapers.escapers().size());
		assertEquals("&lt;x&gt;", Escapers.escapers().get("_html_template_htmlescaper").invoke("<x>"));
	}

}
