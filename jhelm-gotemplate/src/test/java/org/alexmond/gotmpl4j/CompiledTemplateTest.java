package org.alexmond.gotmpl4j;

import java.io.StringWriter;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompiledTemplateTest {

	@Test
	void renderMainTemplateToString() throws Exception {
		GoTemplate template = new GoTemplate();
		template.parse("greeting", "Hello {{ .Name }}");

		assertEquals("Hello world", template.render(Map.of("Name", "world")));
	}

	@Test
	void renderNamedTemplateToString() throws Exception {
		GoTemplate template = new GoTemplate();
		template.parse("greeting", "Hello {{ .Name }}");

		assertEquals("Hello world", template.render("greeting", Map.of("Name", "world")));
	}

	@Test
	void renderUnknownNameThrowsNotFound() throws Exception {
		GoTemplate template = new GoTemplate();
		template.parse("greeting", "Hi");

		assertThrows(TemplateNotFoundException.class, () -> template.render("missing", Map.of()));
	}

	@Test
	void compiledHandleBindsNameAndRenders() throws Exception {
		GoTemplate template = new GoTemplate();
		template.parse("greeting", "Hello {{ .Name }}");

		CompiledTemplate handle = template.compiled("greeting");

		assertEquals("greeting", handle.name());
		assertEquals("Hello world", handle.render(Map.of("Name", "world")));

		StringWriter writer = new StringWriter();
		handle.render(Map.of("Name", "writer"), writer);
		assertEquals("Hello writer", writer.toString());
	}

	@Test
	void compiledUnknownNameThrowsNotFound() throws Exception {
		GoTemplate template = new GoTemplate();
		template.parse("greeting", "Hi");

		assertThrows(TemplateNotFoundException.class, () -> template.compiled("missing"));
	}

}
