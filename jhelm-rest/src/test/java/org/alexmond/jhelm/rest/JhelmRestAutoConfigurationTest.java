package org.alexmond.jhelm.rest;

import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JhelmRestAutoConfigurationTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JhelmCoreAutoConfiguration.class, JhelmRestAutoConfiguration.class));

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

	@Test
	void autoConfigurationRegistersOpenApiMetadata() {
		this.contextRunner.run((context) -> {
			OpenAPI openApi = context.getBean(OpenAPI.class);
			assertNotNull(openApi.getInfo());
			assertEquals("jhelm REST API", openApi.getInfo().getTitle());
			assertEquals("Apache-2.0", openApi.getInfo().getLicense().getName());
			assertNotNull(openApi.getInfo().getVersion());
		});
	}

	@Test
	void openApiMetadataIsOverridable() {
		this.contextRunner.withUserConfiguration(CustomOpenApiConfig.class).run((context) -> {
			OpenAPI openApi = context.getBean(OpenAPI.class);
			assertEquals("Custom API", openApi.getInfo().getTitle());
		});
	}

	@Configuration
	static class CustomOpenApiConfig {

		@Bean
		OpenAPI openAPI() {
			return new OpenAPI().info(new Info().title("Custom API"));
		}

	}

}
