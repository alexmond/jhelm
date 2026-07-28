package org.alexmond.jhelm.kube;

import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.core.JhelmMetricsAutoConfiguration;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.kube.config.JhelmKubernetesProperties;
import org.alexmond.jhelm.kube.service.internal.ObservableKubeService;
import org.alexmond.jhelm.kube.service.internal.RetryableKubeService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the jhelm Kubernetes integration module. Owns the module-wide,
 * client-agnostic concerns — the {@link JhelmKubernetesProperties} binding and the
 * {@link KubeService} decorator chain (see {@link KubeServiceDecorators}) — and imports
 * the client-specific backend configuration(s). The concrete Kubernetes backend is
 * selected by whichever client library is on the classpath:
 * {@link ClientJavaKubeConfiguration} activates when the official
 * {@code io.kubernetes:client-java} is present.
 *
 * <p>
 * The selected backend produces the base {@link KubeService}, which is wrapped with
 * {@link RetryableKubeService} when retry is enabled (the default) and with
 * {@link ObservableKubeService} when a {@link JhelmMetrics} bean is available. Runs
 * before {@link JhelmCoreAutoConfiguration} so that the {@link KubeService} bean is
 * available for its {@code @ConditionalOnBean} checks.
 */
@AutoConfiguration(before = JhelmCoreAutoConfiguration.class, after = JhelmMetricsAutoConfiguration.class)
@EnableConfigurationProperties(JhelmKubernetesProperties.class)
@Import(ClientJavaKubeConfiguration.class)
public class JhelmKubeAutoConfiguration {

	/**
	 * Creates the auto-configuration. Instantiated by the Spring Boot auto-configuration
	 * machinery; not intended to be constructed directly by application code.
	 */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public JhelmKubeAutoConfiguration() {
	}

}
