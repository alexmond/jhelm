package org.alexmond.gotmpl4j.spring;

import org.alexmond.gotmpl4j.spring.view.GoTemplateReactiveViewResolver;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers a {@link GoTemplateReactiveViewResolver} when the application is a reactive
 * (WebFlux) web application and the WebFlux view types are on the classpath. Mirrors
 * Spring Boot's {@code MustacheReactiveWebConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.REACTIVE)
@ConditionalOnClass(GoTemplateReactiveViewResolver.class)
class GoTemplateReactiveWebConfiguration {

	@Bean
	@ConditionalOnMissingBean
	GoTemplateReactiveViewResolver goTemplateReactiveViewResolver(GoTemplateService service,
			Gotmpl4jProperties properties) {
		GoTemplateReactiveViewResolver resolver = new GoTemplateReactiveViewResolver(service);
		// Only override the resolver's defaults when the property is set.
		if (properties.getViewNames() != null) {
			resolver.setViewNames(properties.getViewNames());
		}
		if (properties.getRequestContextAttribute() != null) {
			resolver.setRequestContextAttribute(properties.getRequestContextAttribute());
		}
		if (properties.getCharset() != null) {
			resolver.setCharset(properties.getCharset());
		}
		if (properties.getReactive().getMediaTypes() != null) {
			resolver.setSupportedMediaTypes(properties.getReactive().getMediaTypes());
		}
		resolver.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
		return resolver;
	}

}
