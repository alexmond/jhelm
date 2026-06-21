package org.alexmond.gotmpl4j.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot autoconfiguration for gotmpl4j. Exposes a {@link GoTemplateService} bean so
 * applications can render Go templates via dependency injection. Functions (sprig, and
 * any other ServiceLoader-registered provider on the classpath) are discovered
 * automatically by the engine.
 */
@AutoConfiguration
public class Gotmpl4jAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GoTemplateService goTemplateService() {
		return new GoTemplateService();
	}

}
