package org.alexmond.jhelm.kube;

import java.io.FileReader;
import java.io.IOException;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.core.JhelmMetricsAutoConfiguration;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.kube.config.JhelmKubernetesProperties;
import org.alexmond.jhelm.kube.service.AsyncHelmKubeService;
import org.alexmond.jhelm.kube.service.ObservableKubeService;
import org.alexmond.jhelm.kube.service.RetryableKubeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import java.time.Duration;

/**
 * Auto-configuration for the jhelm Kubernetes integration module. Registers an
 * {@link ApiClient} and a {@link KubeService} implementation. When retry is enabled (the
 * default), the service is wrapped with {@link RetryableKubeService} for transient
 * failure recovery. When a {@link JhelmMetrics} bean is available, the service is further
 * wrapped with {@link ObservableKubeService} for operation timing and counting. Runs
 * before {@link JhelmCoreAutoConfiguration} so that the {@link KubeService} bean is
 * available for its {@code @ConditionalOnBean} checks.
 */
@AutoConfiguration(before = JhelmCoreAutoConfiguration.class, after = JhelmMetricsAutoConfiguration.class)
@EnableConfigurationProperties(JhelmKubernetesProperties.class)
public class JhelmKubeAutoConfiguration {

	/**
	 * Creates the auto-configuration. Instantiated by the Spring Boot auto-configuration
	 * machinery; not intended to be constructed directly by application code.
	 */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public JhelmKubeAutoConfiguration() {
	}

	/**
	 * Builds the Kubernetes {@link ApiClient} used by the module. When a kubeconfig path
	 * is configured the client is built from that file, otherwise the default in-cluster
	 * or local client is used.
	 * @param props the Kubernetes configuration properties, providing the optional
	 * kubeconfig path
	 * @return the configured Kubernetes API client
	 * @throws IOException if the configured kubeconfig file cannot be read
	 */
	@Bean
	@ConditionalOnMissingBean
	public ApiClient apiClient(JhelmKubernetesProperties props) throws IOException {
		if (props.getKubeconfigPath() != null) {
			return Config.fromConfig(KubeConfig.loadKubeConfig(new FileReader(props.getKubeconfigPath())));
		}
		return Config.defaultClient();
	}

	/**
	 * Builds the {@link KubeService} bean. The base {@link AsyncHelmKubeService} is
	 * wrapped with {@link RetryableKubeService} when retry is enabled, and further
	 * wrapped with {@link ObservableKubeService} when a {@link JhelmMetrics} bean is
	 * available.
	 * @param apiClient the configured Kubernetes API client
	 * @param props the Kubernetes configuration properties, providing the retry settings
	 * @param metricsProvider provider for the optional metrics bean used to enable
	 * operation timing and counting
	 * @return the (possibly decorated) Kubernetes service
	 */
	@Bean
	@ConditionalOnMissingBean(KubeService.class)
	public KubeService kubeService(ApiClient apiClient, JhelmKubernetesProperties props,
			ObjectProvider<JhelmMetrics> metricsProvider) {
		AsyncHelmKubeService base = new AsyncHelmKubeService(apiClient);
		KubeService service = base;
		JhelmKubernetesProperties.Retry retryConfig = props.getRetry();
		if (retryConfig.isEnabled()) {
			service = new RetryableKubeService(base, buildRetryTemplate(retryConfig));
		}
		JhelmMetrics metrics = metricsProvider.getIfAvailable();
		if (metrics != null) {
			service = new ObservableKubeService(service, metrics);
		}
		return service;
	}

	private RetryTemplate buildRetryTemplate(JhelmKubernetesProperties.Retry config) {
		RetryPolicy policy = RetryPolicy.builder()
			// maxRetries excludes the initial call, so subtract one to preserve the
			// total invocation count of the old SimpleRetryPolicy(maxAttempts).
			.maxRetries(Math.max(0, config.getMaxAttempts() - 1))
			.delay(Duration.ofMillis(config.getInitialIntervalMs()))
			.multiplier(config.getMultiplier())
			.maxDelay(Duration.ofMillis(config.getMaxIntervalMs()))
			// Only transient errors are retried; non-transient failures stop immediately
			// (replaces the old TransientRetryListener.setExhaustedOnly logic).
			.predicate(RetryableKubeService::isTransient)
			.build();
		return new RetryTemplate(policy);
	}

}
