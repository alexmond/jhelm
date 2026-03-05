package org.alexmond.jhelm.rest;

import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JhelmRestAutoConfigurationTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JhelmRestAutoConfiguration.class));

	@Test
	void autoConfigurationRegistersProperties() {
		this.contextRunner.run((context) -> {
			assertTrue(context.containsBean("jhelm.rest-org.alexmond.jhelm.rest.config.JhelmRestProperties"));
			JhelmRestProperties props = context.getBean(JhelmRestProperties.class);
			assertNotNull(props);
			assertEquals("/api/v1", props.getBasePath());
		});
	}

	@Test
	void customBasePathOverridesDefault() {
		this.contextRunner.withPropertyValues("jhelm.rest.base-path=/custom/api").run((context) -> {
			JhelmRestProperties props = context.getBean(JhelmRestProperties.class);
			assertEquals("/custom/api", props.getBasePath());
		});
	}

	@Test
	void autoConfigurationRegistersExceptionHandler() {
		this.contextRunner.run((context) -> assertNotNull(context.getBean(JhelmRestExceptionHandler.class)));
	}

}
