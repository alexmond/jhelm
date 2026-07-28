package org.alexmond.jhelm.kube;

import java.time.Duration;

import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.kube.config.JhelmKubernetesProperties;
import org.alexmond.jhelm.kube.service.internal.ObservableKubeService;
import org.alexmond.jhelm.kube.service.internal.RetryableKubeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

/**
 * Applies the module-wide, client-agnostic {@link KubeService} decorator chain to a
 * backend's base implementation. Shared by every client backend (official client-java,
 * Fabric8, …) so retry and metrics behave identically regardless of the underlying client
 * library: the backend produces the undecorated service and delegates here for wrapping.
 */
final class KubeServiceDecorators {

	private KubeServiceDecorators() {
	}

	/**
	 * Wraps a backend's base {@link KubeService} with {@link RetryableKubeService} when
	 * retry is enabled, and further with {@link ObservableKubeService} when a
	 * {@link JhelmMetrics} bean is available.
	 * @param base the backend's undecorated service
	 * @param props the Kubernetes configuration properties, providing the retry settings
	 * @param metricsProvider provider for the optional metrics bean used to enable
	 * operation timing and counting
	 * @return the (possibly decorated) Kubernetes service
	 */
	static KubeService decorate(KubeService base, JhelmKubernetesProperties props,
			ObjectProvider<JhelmMetrics> metricsProvider) {
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

	private static RetryTemplate buildRetryTemplate(JhelmKubernetesProperties.Retry config) {
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
