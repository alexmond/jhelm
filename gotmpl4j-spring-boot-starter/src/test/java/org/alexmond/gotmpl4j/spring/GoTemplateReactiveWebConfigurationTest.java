package org.alexmond.gotmpl4j.spring;

import org.alexmond.gotmpl4j.spring.view.GoTemplateReactiveViewResolver;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.core.Ordered;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bean-wiring scenarios for the reactive (WebFlux) view layer, mirroring Spring Boot's
 * {@code MustacheAutoConfigurationReactiveIntegrationTests}: the
 * {@link GoTemplateReactiveViewResolver} is registered in a reactive web context, absent
 * off-web, and backs off to a user-defined resolver.
 */
class GoTemplateReactiveWebConfigurationTest {

	private final ReactiveWebApplicationContextRunner reactive = new ReactiveWebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(Gotmpl4jAutoConfiguration.class));

	private final ApplicationContextRunner nonWeb = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(Gotmpl4jAutoConfiguration.class));

	@Test
	void registersViewResolverInReactiveWebApp() {
		this.reactive.run((context) -> {
			GoTemplateReactiveViewResolver resolver = context.getBean(GoTemplateReactiveViewResolver.class);
			assertEquals(Ordered.LOWEST_PRECEDENCE - 10, resolver.getOrder());
		});
	}

	@Test
	void noViewResolverInNonWebApp() {
		this.nonWeb.run(
				(context) -> assertEquals(0, context.getBeanNamesForType(GoTemplateReactiveViewResolver.class).length));
	}

	@Test
	void backsOffWhenUserDefinesResolver() {
		this.reactive
			.withBean("myResolver", GoTemplateReactiveViewResolver.class,
					() -> new GoTemplateReactiveViewResolver(null))
			.run((context) -> {
				assertEquals(1, context.getBeanNamesForType(GoTemplateReactiveViewResolver.class).length);
				assertTrue(context.containsBean("myResolver"));
			});
	}

}
