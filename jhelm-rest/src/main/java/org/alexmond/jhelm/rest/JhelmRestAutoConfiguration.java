package org.alexmond.jhelm.rest;

import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the jhelm REST API module. Activates only when the application
 * is a web application (servlet-based). Runs after {@link JhelmCoreAutoConfiguration} so
 * that all core beans (actions, services) are available for REST controllers.
 */
@AutoConfiguration(after = JhelmCoreAutoConfiguration.class)
@ConditionalOnWebApplication
@EnableConfigurationProperties(JhelmRestProperties.class)
public class JhelmRestAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public JhelmRestExceptionHandler jhelmRestExceptionHandler() {
		return new JhelmRestExceptionHandler();
	}

}
