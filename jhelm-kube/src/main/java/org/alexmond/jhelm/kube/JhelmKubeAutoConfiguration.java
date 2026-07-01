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
import org.alexmond.jhelm.kube.service.KubeClient;
import org.alexmond.jhelm.kube.service.KubernetesHealthIndicator;
import org.alexmond.jhelm.kube.service.internal.ObservableKubeService;
import org.alexmond.jhelm.kube.service.internal.RetryableKubeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import java.time.Duration;

/**
 * Auto-configuration for the jhelm Kubernetes integration module. Registers a
 * {@link KubeClient} and a {@link KubeService} implementation. When retry is enabled (the
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
	 * Registers the {@link KubeClient} used by the module. If a consumer supplies their
	 * own {@link ApiClient} bean (the intentional, documented customization seam) it is
	 * wrapped as-is; otherwise a client is built from the configured kubeconfig path,
	 * falling back to the default in-cluster or local client.
	 * @param props the Kubernetes configuration properties, providing the optional
	 * kubeconfig path
	 * @param apiClientOverride provider for an optional consumer-supplied API client used
	 * to override the built-in client
	 * @return the registered {@link KubeClient}
	 * @throws IOException if the configured kubeconfig file cannot be read
	 */
	@Bean
	@ConditionalOnMissingBean
	public KubeClient kubeClient(JhelmKubernetesProperties props, ObjectProvider<ApiClient> apiClientOverride)
			throws IOException {
		ApiClient override = apiClientOverride.getIfAvailable();
		ApiClient client = (override != null) ? override : buildApiClient(props);
		return new KubeClient(client);
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
	private ApiClient buildApiClient(JhelmKubernetesProperties props) throws IOException {
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
	 * @param kubeClient the registered Kubernetes client wrapper
	 * @param props the Kubernetes configuration properties, providing the retry settings
	 * @param metricsProvider provider for the optional metrics bean used to enable
	 * operation timing and counting
	 * @return the (possibly decorated) Kubernetes service
	 */
	@Bean
	@ConditionalOnMissingBean(KubeService.class)
	public KubeService kubeService(KubeClient kubeClient, JhelmKubernetesProperties props,
			ObjectProvider<JhelmMetrics> metricsProvider) {
		AsyncHelmKubeService base = new AsyncHelmKubeService(kubeClient);
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

	/**
	 * Kubernetes health-indicator configuration, isolated in a nested class guarded by
	 * {@link ConditionalOnClass} so the actuator {@code HealthIndicator} type is only
	 * referenced when Spring Boot Actuator is on the classpath. This keeps the enclosing
	 * {@link JhelmKubeAutoConfiguration} introspectable by consumers (e.g. the CLI) that
	 * do not depend on actuator.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HealthIndicator.class)
	static class KubernetesHealthContributorConfiguration {

		/**
		 * Registers a Kubernetes connectivity health indicator reporting up/down based on
		 * reaching the cluster {@code /version} endpoint.
		 * @param kubeClient the client wrapper to probe
		 * @return the health indicator bean
		 */
		@Bean
		@ConditionalOnBean(KubeClient.class)
		@ConditionalOnMissingBean(KubernetesHealthIndicator.class)
		KubernetesHealthIndicator kubernetesHealthIndicator(KubeClient kubeClient) {
			return new KubernetesHealthIndicator(kubeClient);
		}

	}

}
