package org.alexmond.jhelm.core;

import io.micrometer.core.instrument.MeterRegistry;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for jhelm metrics. Creates a {@link JhelmMetrics} bean when a
 * {@link MeterRegistry} is available on the classpath and in the application context.
 * Runs before both core and Kubernetes auto-configurations so metrics are available for
 * instrumentation.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class JhelmMetricsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(MeterRegistry.class)
	public JhelmMetrics jhelmMetrics(MeterRegistry meterRegistry) {
		return new JhelmMetrics(meterRegistry);
	}

}
