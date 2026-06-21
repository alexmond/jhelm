package org.alexmond.gotmpl4j.spring;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Gotmpl4jAutoConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(Gotmpl4jAutoConfiguration.class));

	@Test
	void autoConfiguresServiceAndRendersWithSprigFunction() {
		runner.run((context) -> {
			GoTemplateService service = context.getBean(GoTemplateService.class);
			// `.Name` proves data binding; `upper` proves sprig functions resolve via the
			// ServiceLoader through the starter.
			String out = service.render("t", "Hello {{ .Name | upper }}", Map.of("Name", "world"));
			assertEquals("Hello WORLD", out);
		});
	}

	@Test
	void renderWrapsEngineFailuresInGoTemplateException() {
		runner.run((context) -> {
			GoTemplateService service = context.getBean(GoTemplateService.class);
			// An unterminated action is a parse error; the checked engine exception is
			// wrapped as an unchecked GoTemplateException for callers.
			assertThrows(GoTemplateException.class, () -> service.render("bad", "{{ .Name ", Map.of()));
		});
	}

}
