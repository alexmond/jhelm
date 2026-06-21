package org.alexmond.gotmpl4j.html;

import org.junit.jupiter.api.Test;

import org.alexmond.gotmpl4j.GoTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end tests for the contextual escape pass: parse a template, run {@link Escaper},
 * render, and check the output matches what Go's {@code html/template} would produce.
 */
class EscaperTest {

	private static String render(String template, Object data) throws Exception {
		GoTemplate t = GoTemplate.builder().withFunctions(Escapers.escapers()).build();
		t.parse("doc", template);
		Escaper.escape(t.getRootNodes(), "doc");
		return t.render("doc", data);
	}

	@Test
	void htmlTextContext() throws Exception {
		assertEquals("<p>&lt;b&gt;&amp;</p>", render("<p>{{.}}</p>", "<b>&"));
	}

	@Test
	void strayLessThanInTextIsEscaped() throws Exception {
		assertEquals("I &lt;3 you", render("I <3 {{.}}", "you"));
	}

	@Test
	void quotedUrlAttributeSafe() throws Exception {
		assertEquals("<a href=\"/path\">x</a>", render("<a href=\"{{.}}\">x</a>", "/path"));
	}

	@Test
	void quotedUrlAttributeUnsafeSchemeDefanged() throws Exception {
		assertEquals("<a href=\"#ZgotmplZ\">x</a>", render("<a href=\"{{.}}\">x</a>", "javascript:alert(1)"));
	}

	@Test
	void jsValueContext() throws Exception {
		// The value is JSON-encoded for a JS value context, with < escaped.
		assertEquals("<script>x = \"a\\u003cb\"</script>", render("<script>x = {{.}}</script>", "a<b"));
	}

	@Test
	void quotedAttributeText() throws Exception {
		assertEquals("<div title=\"a&#34;b\">", render("<div title=\"{{.}}\">", "a\"b"));
	}

	@Test
	void ifBranchesSameContext() throws Exception {
		assertEquals("<b>hi</b>", render("<b>{{if .}}{{.}}{{else}}x{{end}}</b>", "hi"));
	}

	@Test
	void templateCallInTextContextIsEscaped() throws Exception {
		GoTemplate t = GoTemplate.builder().withFunctions(Escapers.escapers()).build();
		t.parse("doc", "<p>{{template \"inner\" .}}</p>");
		t.parse("inner", "{{.}}");
		Escaper.escape(t.getRootNodes(), "doc");
		assertEquals("<p>&lt;b&gt;</p>", t.render("doc", "<b>"));
	}

	@Test
	void cssValueContext() throws Exception {
		assertEquals("<p style=\"color: red\">", render("<p style=\"color: {{.}}\">", "red"));
	}

	@Test
	void cssUrlContext() throws Exception {
		assertEquals("<div style=\"background: url(/img.png)\">",
				render("<div style=\"background: url({{.}})\">", "/img.png"));
	}

	@Test
	void jsStringContext() throws Exception {
		assertEquals("<script>var s = \"a\\u0022b\";</script>", render("<script>var s = \"{{.}}\";</script>", "a\"b"));
	}

	@Test
	void unquotedAttributeUsesNospaceEscaper() throws Exception {
		assertEquals("<input value=a&#32;b>", render("<input value={{.}}>", "a b"));
	}

	@Test
	void rangeLoop() throws Exception {
		assertEquals("<ul><li>a</li><li>&lt;b&gt;</li></ul>",
				render("<ul>{{range .}}<li>{{.}}</li>{{end}}</ul>", java.util.List.of("a", "<b>")));
	}

	@Test
	void withBlock() throws Exception {
		assertEquals("<p>&lt;hi&gt;</p>", render("<p>{{with .}}{{.}}{{end}}</p>", "<hi>"));
	}

	@Test
	void htmlCommentsAreStripped() throws Exception {
		// Go html/template removes HTML comments from the output entirely.
		assertEquals("beforeafter", render("before<!-- c -->after", null));
	}

	@Test
	void doctypeLessThanIsNotEscaped() throws Exception {
		assertEquals("<!DOCTYPE html><p>x</p>", render("<!DOCTYPE html><p>{{.}}</p>", "x"));
	}

	@Test
	void predefinedHtmlEscaperIsMergedNotDuplicated() throws Exception {
		// {{. | html}} in HTML text: the predefined "html" is equivalent to the
		// contextual
		// htmlescaper, so the pipeline is not double-escaped.
		assertEquals("<p>&lt;b&gt;</p>", render("<p>{{. | html}}</p>", "<b>"));
	}

	@Test
	void endingInNonTextContextIsAnError() {
		// An unterminated attribute leaves the template in a non-text end context.
		assertThrows(EscapeError.class, () -> render("<a href=\"{{.}}", "x"));
	}

}
