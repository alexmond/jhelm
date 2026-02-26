package org.alexmond.jhelm.core.metrics;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Central metrics service for jhelm. Wraps a {@link MeterRegistry} and provides
 * convenience methods for recording render timers, cache statistics, and Kubernetes
 * operation metrics.
 */
public class JhelmMetrics {

	private static final String PREFIX = "jhelm";

	private final MeterRegistry registry;

	private final Timer renderTimer;

	private final Counter cacheHitCounter;

	private final Counter cacheMissCounter;

	public JhelmMetrics(MeterRegistry registry) {
		this.registry = registry;
		this.renderTimer = Timer.builder(PREFIX + ".engine.render")
			.description("Time spent rendering Helm chart templates")
			.register(registry);
		this.cacheHitCounter = Counter.builder(PREFIX + ".cache.requests")
			.description("Template cache requests")
			.tag("result", "hit")
			.register(registry);
		this.cacheMissCounter = Counter.builder(PREFIX + ".cache.requests")
			.description("Template cache requests")
			.tag("result", "miss")
			.register(registry);
	}

	/**
	 * Record the duration of a chart render operation.
	 * @param durationNanos the duration in nanoseconds
	 */
	public void recordRender(long durationNanos) {
		renderTimer.record(durationNanos, TimeUnit.NANOSECONDS);
	}

	/**
	 * Time a render operation and return its result.
	 * @param <T> the result type
	 * @param supplier the render operation
	 * @return the result
	 */
	public <T> T timeRender(Supplier<T> supplier) {
		return renderTimer.record(supplier);
	}

	/**
	 * Record a template cache hit.
	 */
	public void recordCacheHit() {
		cacheHitCounter.increment();
	}

	/**
	 * Record a template cache miss.
	 */
	public void recordCacheMiss() {
		cacheMissCounter.increment();
	}

	/**
	 * Register a gauge that tracks the current template cache size.
	 * @param sizeSupplier supplies the current cache size
	 */
	public void registerCacheSizeGauge(Supplier<Number> sizeSupplier) {
		io.micrometer.core.instrument.Gauge.builder(PREFIX + ".cache.size", sizeSupplier)
			.description("Current template cache size")
			.register(registry);
	}

	/**
	 * Create a timer for the given Kubernetes operation.
	 * @param operation the operation name (e.g. "apply", "delete", "store")
	 * @return the timer
	 */
	public Timer kubeOperationTimer(String operation) {
		return Timer.builder(PREFIX + ".kube.operation")
			.description("Kubernetes operation duration")
			.tag("operation", operation)
			.register(registry);
	}

	/**
	 * Create a counter for the given Kubernetes operation outcome.
	 * @param operation the operation name
	 * @param outcome "success" or "error"
	 * @return the counter
	 */
	public Counter kubeOperationCounter(String operation, String outcome) {
		return Counter.builder(PREFIX + ".kube.operations")
			.description("Kubernetes operation count")
			.tag("operation", operation)
			.tag("outcome", outcome)
			.register(registry);
	}

	/**
	 * Return the underlying {@link MeterRegistry}.
	 */
	public MeterRegistry getRegistry() {
		return registry;
	}

}
