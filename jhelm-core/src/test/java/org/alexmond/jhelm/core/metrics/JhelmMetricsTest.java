package org.alexmond.jhelm.core.metrics;

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

class JhelmMetricsTest {

	private MeterRegistry registry;

	private JhelmMetrics metrics;

	@BeforeEach
	void setUp() {
		registry = new SimpleMeterRegistry();
		metrics = new JhelmMetrics(registry);
	}

	@Test
	void testRenderTimerRecorded() {
		metrics.recordRender(1_000_000L);
		Timer timer = registry.find("jhelm.engine.render").timer();
		assertNotNull(timer);
		assertEquals(1, timer.count());
	}

	@Test
	void testTimeRenderReturnsResult() {
		String result = metrics.timeRender(() -> "rendered");
		assertEquals("rendered", result);
		Timer timer = registry.find("jhelm.engine.render").timer();
		assertNotNull(timer);
		assertEquals(1, timer.count());
	}

	@Test
	void testCacheHitCounter() {
		metrics.recordCacheHit();
		metrics.recordCacheHit();
		Counter counter = registry.find("jhelm.cache.requests").tag("result", "hit").counter();
		assertNotNull(counter);
		assertEquals(2.0, counter.count());
	}

	@Test
	void testCacheMissCounter() {
		metrics.recordCacheMiss();
		Counter counter = registry.find("jhelm.cache.requests").tag("result", "miss").counter();
		assertNotNull(counter);
		assertEquals(1.0, counter.count());
	}

	@Test
	void testCacheSizeGauge() {
		AtomicInteger size = new AtomicInteger(5);
		metrics.registerCacheSizeGauge(size::get);
		Gauge gauge = registry.find("jhelm.cache.size").gauge();
		assertNotNull(gauge);
		assertEquals(5.0, gauge.value());
		size.set(10);
		assertEquals(10.0, gauge.value());
	}

	@Test
	void testKubeOperationTimer() {
		Timer timer = metrics.kubeOperationTimer("apply");
		assertNotNull(timer);
		timer.record(Duration.ofMillis(100));
		assertEquals(1, timer.count());
	}

	@Test
	void testKubeOperationCounter() {
		Counter counter = metrics.kubeOperationCounter("apply", "success");
		assertNotNull(counter);
		counter.increment();
		assertEquals(1.0, counter.count());
	}

	@Test
	void testGetRegistry() {
		assertEquals(registry, metrics.getRegistry());
	}

	@Test
	void testMultipleRenderRecordings() {
		metrics.recordRender(1_000_000L);
		metrics.recordRender(2_000_000L);
		metrics.recordRender(3_000_000L);
		Timer timer = registry.find("jhelm.engine.render").timer();
		assertNotNull(timer);
		assertEquals(3, timer.count());
		assertTrue(timer.totalTime(TimeUnit.NANOSECONDS) > 0);
	}

}
