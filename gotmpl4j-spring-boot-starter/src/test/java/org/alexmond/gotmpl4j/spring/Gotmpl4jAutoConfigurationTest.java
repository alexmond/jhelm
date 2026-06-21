package org.alexmond.gotmpl4j.spring;

import java.util.Locale;
import java.util.Map;

import org.alexmond.gotmpl4j.Function;
import org.alexmond.gotmpl4j.FunctionProvider;
import org.alexmond.gotmpl4j.GoTemplate;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
	void rendersViewLoadedByNameWithReferencedTemplate() {
		runner.run((context) -> {
			GoTemplateService service = context.getBean(GoTemplateService.class);
			// `hello.gotmpl` lives under the default classpath:/templates/ location and
			// pulls in `layouts/footer.gotmpl` via {{ template }} — proving the loader
			// assembles the whole location into one set and resolves a view by name.
			String out = service.render("hello", Map.of("Name", "world", "Site", "gotmpl4j"));
			assertEquals("Hi WORLD from gotmpl4j", out);
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

	@Test
	void registersFunctionProviderBeanWithTheEngine() {
		runner.withUserConfiguration(CustomFunctionsConfiguration.class).run((context) -> {
			GoTemplateService service = context.getBean(GoTemplateService.class);
			// `shout` is contributed by a Spring FunctionProvider bean, proving the
			// starter
			// bridges context function beans into the engine (alongside sprig's `upper`).
			String out = service.render("t", "{{ shout .Name }}", Map.of("Name", "world"));
			assertEquals("WORLD!", out);
		});
	}

	@Configuration
	static class CustomFunctionsConfiguration {

		@Bean
		FunctionProvider shoutProvider() {
			return new FunctionProvider() {
				@Override
				public Map<String, Function> getFunctions(GoTemplate template) {
					return Map.of("shout", (args) -> String.valueOf(args[0]).toUpperCase(Locale.ROOT) + "!");
				}

				@Override
				public int priority() {
					return 300;
				}
			};
		}

	}

}
