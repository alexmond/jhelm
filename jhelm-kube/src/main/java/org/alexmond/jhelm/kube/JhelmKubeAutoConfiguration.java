package org.alexmond.jhelm.kube;

import java.io.FileReader;
import java.io.IOException;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
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
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

/**
 * Auto-configuration for the jhelm Kubernetes integration module. Registers an
 * {@link ApiClient} and a {@link KubeService} implementation. When retry is enabled (the
 * default), the service is wrapped with {@link RetryableKubeService} for transient
 * failure recovery. When a {@link JhelmMetrics} bean is available, the service is further
 * wrapped with {@link ObservableKubeService} for operation timing and counting. Runs
 * before {@link JhelmCoreAutoConfiguration} so that the {@link KubeService} bean is
 * available for its {@code @ConditionalOnBean} checks.
 */
@AutoConfiguration(before = JhelmCoreAutoConfiguration.class,
		after = org.alexmond.jhelm.core.JhelmMetricsAutoConfiguration.class)
@EnableConfigurationProperties(JhelmKubernetesProperties.class)
public class JhelmKubeAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ApiClient apiClient(JhelmKubernetesProperties props) throws IOException {
		if (props.getKubeconfigPath() != null) {
			return Config.fromConfig(KubeConfig.loadKubeConfig(new FileReader(props.getKubeconfigPath())));
		}
		return Config.defaultClient();
	}

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
		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(config.getMaxAttempts());

		ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
		backOff.setInitialInterval(config.getInitialIntervalMs());
		backOff.setMultiplier(config.getMultiplier());
		backOff.setMaxInterval(config.getMaxIntervalMs());

		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(retryPolicy);
		template.setBackOffPolicy(backOff);
		template.registerListener(new TransientRetryListener());
		return template;
	}

	/**
	 * A retry listener that only allows retries for transient errors.
	 */
	private static final class TransientRetryListener implements RetryListener {

		@Override
		public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
			return true;
		}

		@Override
		public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
				Throwable throwable) {
			if (!RetryableKubeService.isTransient(throwable)) {
				context.setExhaustedOnly();
			}
		}

	}

}
