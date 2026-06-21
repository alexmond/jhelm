package org.alexmond.gotmpl4j.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

/**
 * Spring Boot autoconfiguration for gotmpl4j. Exposes a {@link GoTemplateLoader} (loads
 * templates from {@code gotmpl4j.template-location}) and a {@link GoTemplateService} bean
 * so applications can render Go templates via dependency injection. Functions (sprig, and
 * any other ServiceLoader-registered provider on the classpath) are discovered
 * automatically by the engine.
 *
 * <p>
 * The compile cache is owned by the engine's {@link org.alexmond.gotmpl4j.TemplateCache};
 * {@code gotmpl4j.cache} simply toggles it.
 */
@AutoConfiguration
@EnableConfigurationProperties(Gotmpl4jProperties.class)
@ConditionalOnProperty(prefix = "gotmpl4j", name = "enabled", matchIfMissing = true)
public class Gotmpl4jAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GoTemplateLoader goTemplateLoader(ResourceLoader resourceLoader, Gotmpl4jProperties properties) {
		return new GoTemplateLoader(resourceLoader, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public GoTemplateService goTemplateService(GoTemplateLoader loader, Gotmpl4jProperties properties) {
		return new GoTemplateService(loader, properties.isCache());
	}

}
