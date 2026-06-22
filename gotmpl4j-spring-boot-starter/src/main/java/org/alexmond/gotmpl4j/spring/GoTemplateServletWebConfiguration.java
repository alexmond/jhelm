package org.alexmond.gotmpl4j.spring;

import org.alexmond.gotmpl4j.spring.view.GoTemplateViewResolver;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers a {@link GoTemplateViewResolver} when the application is a servlet web
 * application and Spring MVC's view types are on the classpath. Mirrors Spring Boot's
 * {@code MustacheServletWebConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(GoTemplateViewResolver.class)
class GoTemplateServletWebConfiguration {

	@Bean
	@ConditionalOnMissingBean
	GoTemplateViewResolver goTemplateViewResolver(GoTemplateService service, Gotmpl4jProperties properties) {
		Gotmpl4jProperties.Servlet servlet = properties.getServlet();
		GoTemplateViewResolver resolver = new GoTemplateViewResolver(service);
		resolver.setContentType(servlet.getContentType());
		resolver.setViewNames(properties.getViewNames());
		resolver.setRequestContextAttribute(properties.getRequestContextAttribute());
		resolver.setExposeRequestAttributes(servlet.isExposeRequestAttributes());
		resolver.setExposeSessionAttributes(servlet.isExposeSessionAttributes());
		resolver.setAllowRequestOverride(servlet.isAllowRequestOverride());
		resolver.setAllowSessionOverride(servlet.isAllowSessionOverride());
		resolver.setCache(properties.isCache());
		resolver.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
		return resolver;
	}

}
