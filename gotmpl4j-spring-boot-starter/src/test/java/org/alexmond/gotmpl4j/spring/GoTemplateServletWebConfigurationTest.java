package org.alexmond.gotmpl4j.spring;

import org.alexmond.gotmpl4j.spring.view.GoTemplateViewResolver;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.Ordered;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bean-wiring scenarios for the servlet view layer, mirroring Spring Boot's
 * {@code MustacheAutoConfigurationTests}: the {@link GoTemplateViewResolver} is
 * registered in a servlet web context, absent off-web, and backs off to a user-defined
 * resolver.
 */
class GoTemplateServletWebConfigurationTest {

	private final WebApplicationContextRunner web = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(Gotmpl4jAutoConfiguration.class));

	private final ApplicationContextRunner nonWeb = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(Gotmpl4jAutoConfiguration.class));

	@Test
	void registersViewResolverInServletWebApp() {
		this.web.run((context) -> {
			GoTemplateViewResolver resolver = context.getBean(GoTemplateViewResolver.class);
			assertEquals(Ordered.LOWEST_PRECEDENCE - 10, resolver.getOrder());
		});
	}

	@Test
	void noViewResolverInNonWebApp() {
		this.nonWeb.run((context) -> assertEquals(0, context.getBeanNamesForType(GoTemplateViewResolver.class).length));
	}

	@Test
	void backsOffWhenUserDefinesResolver() {
		this.web.withBean("myResolver", GoTemplateViewResolver.class, () -> new GoTemplateViewResolver(null))
			.run((context) -> {
				assertEquals(1, context.getBeanNamesForType(GoTemplateViewResolver.class).length);
				assertTrue(context.containsBean("myResolver"));
			});
	}

}
