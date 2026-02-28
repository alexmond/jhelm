package org.alexmond.jhelm.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal Spring configuration that activates {@link JhelmTestProperties} binding for KPS
 * comparison tests.
 */
@Configuration
@EnableConfigurationProperties(JhelmTestProperties.class)
public class KpsTestConfig {

}
