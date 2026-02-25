package org.alexmond.jhelm.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Legacy configuration bridge. Delegates to {@link JhelmCoreAutoConfiguration}. Retained
 * for backwards compatibility with tests that reference this class directly.
 */
@Configuration
@Import(JhelmCoreAutoConfiguration.class)
public class CoreConfig {

}
