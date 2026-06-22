package org.alexmond.gotmpl4j;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import org.alexmond.gotmpl4j.html.EscapeError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the opt-in {@code html/template} mode on {@link GoTemplate} (epic #414 S6): when
 * enabled the engine contextually auto-escapes; by default it stays {@code text/template}
 * (no escaping), preserving Helm parity.
 */
class HtmlModeTest {

	private static String render(GoTemplate t, String name, Object data) throws Exception {
		StringWriter w = new StringWriter();
		t.execute(name, data, w);
		return w.toString();
	}

	@Test
	void defaultModeDoesNotEscape() throws Exception {
		// Plain text/template: values are emitted verbatim (this is what Helm relies on).
		GoTemplate t = new GoTemplate();
		t.parse("doc", "<p>{{.}}</p>");
		assertEquals("<p><b></p>", render(t, "doc", "<b>"));
	}

	@Test
	void htmlModeEscapesByContext() throws Exception {
		GoTemplate t = GoTemplate.builder().htmlEscaping().build();
		t.parse("doc", "<p>{{.}}</p><a href=\"{{.}}\">x</a>");
		assertEquals("<p>&lt;b&gt;</p><a href=\"%3cb%3e\">x</a>", render(t, "doc", "<b>"));
	}

	@Test
	void htmlModeIsIdempotentAcrossRenders() throws Exception {
		GoTemplate t = GoTemplate.builder().htmlEscaping().build();
		t.parse("doc", "<p>{{.}}</p>");
		assertEquals("<p>&lt;b&gt;</p>", render(t, "doc", "<b>"));
		// A second render must not double-escape (escape runs once, then is cached).
		assertEquals("<p>&lt;a&gt;</p>", render(t, "doc", "<a>"));
	}

	@Test
	void htmlModeEscapesMultipleViewsSharingAPartial() throws Exception {
		GoTemplate t = GoTemplate.builder().htmlEscaping().build();
		t.parse("a", "<p>{{template \"shared\" .}}|{{.}}</p>");
		t.parse("b", "<div>{{template \"shared\" .}}</div>");
		t.parse("shared", "{{.}}");
		assertEquals("<p>&lt;x&gt;|&lt;x&gt;</p>", render(t, "a", "<x>"));
		assertEquals("<div>&lt;x&gt;</div>", render(t, "b", "<x>"));
	}

	@Test
	void htmlModeReportsNonTextEndContext() {
		GoTemplate t = GoTemplate.builder().htmlEscaping().build();
		assertThrows(EscapeError.class, () -> {
			t.parse("doc", "<a href=\"{{.}}");
			render(t, "doc", "x");
		});
	}

}
