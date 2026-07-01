package org.alexmond.jhelm.core.metrics;

import java.io.IOException;
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

	/**
	 * Creates the metrics service and registers the render timer and cache counters on
	 * the given registry.
	 * @param registry the Micrometer registry to publish metrics to
	 */
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
	 * Times an action-layer operation (install, upgrade, uninstall, rollback), recording
	 * its duration on {@code jhelm.action} and incrementing {@code jhelm.actions} with
	 * the outcome. The operation's own exceptions propagate unchanged (counted as
	 * {@code error}).
	 * @param <T> the result type
	 * @param action the action name (e.g. "install", "upgrade")
	 * @param op the action to run
	 * @return the action result
	 */
	public <T> T timeAction(String action, Supplier<T> op) {
		boolean success = false;
		try {
			T result = actionTimer(action).record(op);
			success = true;
			return result;
		}
		finally {
			actionCounter(action, success ? "success" : "error").increment();
		}
	}

	/**
	 * Create a timer for the given action-layer operation.
	 * @param action the action name
	 * @return the timer
	 */
	public Timer actionTimer(String action) {
		return Timer.builder(PREFIX + ".action")
			.description("Helm action-layer operation duration")
			.tag("action", action)
			.register(registry);
	}

	/**
	 * Create a counter for the given action-layer outcome.
	 * @param action the action name
	 * @param outcome "success" or "error"
	 * @return the counter
	 */
	public Counter actionCounter(String action, String outcome) {
		return Counter.builder(PREFIX + ".actions")
			.description("Helm action-layer operation count")
			.tag("action", action)
			.tag("outcome", outcome)
			.register(registry);
	}

	/**
	 * Times a chart pull, recording its duration on {@code jhelm.chart.pull} and
	 * incrementing {@code jhelm.chart.pulls} with the outcome. Checked
	 * {@link IOException} (and any runtime exception) from the pull propagates unchanged
	 * (counted as {@code error}).
	 * @param source the pull source ("http" or "oci")
	 * @param op the pull to run
	 * @throws IOException if the pull fails
	 */
	public void timeChartPull(String source, IoRunnable op) throws IOException {
		Timer.Sample sample = Timer.start(registry);
		boolean success = false;
		try {
			op.run();
			success = true;
		}
		finally {
			sample.stop(chartPullTimer(source));
			chartPullCounter(source, success ? "success" : "error").increment();
		}
	}

	/**
	 * Create a timer for chart pulls from the given source.
	 * @param source the pull source ("http" or "oci")
	 * @return the timer
	 */
	public Timer chartPullTimer(String source) {
		return Timer.builder(PREFIX + ".chart.pull")
			.description("Chart pull duration")
			.tag("source", source)
			.register(registry);
	}

	/**
	 * Create a counter for chart pull outcomes from the given source.
	 * @param source the pull source ("http" or "oci")
	 * @param outcome "success" or "error"
	 * @return the counter
	 */
	public Counter chartPullCounter(String source, String outcome) {
		return Counter.builder(PREFIX + ".chart.pulls")
			.description("Chart pull count")
			.tag("source", source)
			.tag("outcome", outcome)
			.register(registry);
	}

	/**
	 * Returns the underlying {@link MeterRegistry}.
	 * @return the registry backing this metrics service
	 */
	public MeterRegistry getRegistry() {
		return registry;
	}

	/**
	 * A chart-pull operation that may throw {@link IOException}. Enables
	 * {@link #timeChartPull(String, IoRunnable)} to wrap the checked download calls.
	 */
	@FunctionalInterface
	public interface IoRunnable {

		/**
		 * Runs the pull.
		 * @throws IOException if the pull fails
		 */
		void run() throws IOException;

	}

}
