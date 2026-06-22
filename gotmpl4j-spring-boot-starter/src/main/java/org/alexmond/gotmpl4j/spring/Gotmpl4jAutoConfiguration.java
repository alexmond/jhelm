package org.alexmond.gotmpl4j.spring;

import java.util.List;
import java.util.Map;

import org.alexmond.gotmpl4j.Function;
import org.alexmond.gotmpl4j.FunctionProvider;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;

/**
 * Spring Boot autoconfiguration for gotmpl4j. Exposes a {@link GoTemplateFactory}
 * (bridges Spring function beans into the engine), a {@link GoTemplateLoader} (loads
 * templates from {@code gotmpl4j.template-location}) and a {@link GoTemplateService} bean
 * so applications can render Go templates via dependency injection.
 *
 * <p>
 * Functions are extended the Spring-idiomatic way: any {@link FunctionProvider} bean in
 * the context is registered with the engine, on top of the providers the engine discovers
 * via {@code ServiceLoader} (sprig, helm). This mirrors how Thymeleaf collects
 * {@code IDialect} beans — the engine owns the SPI, the starter only feeds beans into it.
 *
 * <p>
 * The compile cache is owned by the engine's {@link org.alexmond.gotmpl4j.TemplateCache};
 * {@code gotmpl4j.cache} simply toggles it.
 */
@AutoConfiguration
@EnableConfigurationProperties(Gotmpl4jProperties.class)
@ConditionalOnProperty(prefix = "gotmpl4j", name = "enabled", matchIfMissing = true)
@Import(GoTemplateServletWebConfiguration.class)
public class Gotmpl4jAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GoTemplateFactory goTemplateFactory(ObjectProvider<FunctionProvider> providers,
			ObjectProvider<Map<String, Function>> extraFunctions) {
		List<FunctionProvider> providerBeans = providers.orderedStream().toList();
		Map<String, Function> functions = extraFunctions.getIfAvailable(Map::of);
		return new GoTemplateFactory(providerBeans, functions);
	}

	@Bean
	@ConditionalOnMissingBean
	public GoTemplateLoader goTemplateLoader(ResourceLoader resourceLoader, Gotmpl4jProperties properties,
			GoTemplateFactory factory) {
		return new GoTemplateLoader(resourceLoader, properties, factory);
	}

	@Bean
	@ConditionalOnMissingBean
	public GoTemplateService goTemplateService(GoTemplateLoader loader, GoTemplateFactory factory,
			Gotmpl4jProperties properties) {
		return new GoTemplateService(loader, factory, properties.isCache());
	}

}
