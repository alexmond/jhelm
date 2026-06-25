package org.alexmond.jhelm.kube;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Legacy configuration bridge. Delegates to {@link JhelmKubeAutoConfiguration}. Retained
 * for backwards compatibility with tests that reference this class directly.
 */
@Configuration
@Import(JhelmKubeAutoConfiguration.class)
public class KubernetesConfig {

	/**
	 * Creates the configuration bridge. Instantiated by the Spring container.
	 */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public KubernetesConfig() {
	}

}
