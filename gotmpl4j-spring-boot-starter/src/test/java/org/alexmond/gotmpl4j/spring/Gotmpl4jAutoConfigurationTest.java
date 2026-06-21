package org.alexmond.gotmpl4j.spring;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

}
