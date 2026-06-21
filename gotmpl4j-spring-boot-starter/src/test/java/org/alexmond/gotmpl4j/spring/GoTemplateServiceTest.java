package org.alexmond.gotmpl4j.spring;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoTemplateServiceTest {

	@Test
	void inlineRenderResolvesData() {
		GoTemplateService service = new GoTemplateService();

		assertEquals("Hello world", service.render("t", "Hello {{ .Name }}", Map.of("Name", "world")));
	}

	@Test
	void inlineRenderWrapsParseFailure() {
		GoTemplateService service = new GoTemplateService();

		assertThrows(GoTemplateException.class, () -> service.render("bad", "{{ .Name ", Map.of()));
	}

	@Test
	void viewRenderWithoutLoaderIsRejected() {
		GoTemplateService service = new GoTemplateService();

		assertThrows(GoTemplateException.class, () -> service.render("view", Map.of()));
	}

}
