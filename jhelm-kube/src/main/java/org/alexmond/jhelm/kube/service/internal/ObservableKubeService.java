package org.alexmond.jhelm.kube.service.internal;

import java.util.List;
import java.util.Optional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.KubeService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A {@link KubeService} decorator that records operation timers and success/error
 * counters using {@link JhelmMetrics}.
 */
@Slf4j
public class ObservableKubeService implements KubeService {

	private final KubeService delegate;

	private final Timer applyTimer;

	private final Timer deleteTimer;

	private final Timer storeTimer;

	private final Timer getTimer;

	private final Timer listTimer;

	private final Timer historyTimer;

	private final Counter successCounter;

	private final Counter errorCounter;

	/**
	 * Creates a metrics-recording decorator around the given delegate.
	 * @param delegate the underlying {@link KubeService} to which calls are forwarded
	 * @param metrics the metrics registry used to build the per-operation timers and
	 * success/error counters
	 */
	public ObservableKubeService(KubeService delegate, JhelmMetrics metrics) {
		this.delegate = delegate;
		this.applyTimer = metrics.kubeOperationTimer("apply");
		this.deleteTimer = metrics.kubeOperationTimer("delete");
		this.storeTimer = metrics.kubeOperationTimer("store");
		this.getTimer = metrics.kubeOperationTimer("get");
		this.listTimer = metrics.kubeOperationTimer("list");
		this.historyTimer = metrics.kubeOperationTimer("history");
		this.successCounter = metrics.kubeOperationCounter("all", "success");
		this.errorCounter = metrics.kubeOperationCounter("all", "error");
	}

	@Override
	public void storeRelease(Release release) {
		timeVoid(storeTimer, () -> delegate.storeRelease(release));
	}

	@Override
	public Optional<Release> getRelease(String name, String namespace) {
		return time(getTimer, () -> delegate.getRelease(name, namespace));
	}

	@Override
	public List<Release> listReleases(String namespace) {
		return time(listTimer, () -> delegate.listReleases(namespace));
	}

	@Override
	public List<Release> getReleaseHistory(String name, String namespace) {
		return time(historyTimer, () -> delegate.getReleaseHistory(name, namespace));
	}

	@Override
	public void deleteReleaseHistory(String name, String namespace) {
		timeVoid(deleteTimer, () -> delegate.deleteReleaseHistory(name, namespace));
	}

	@Override
	public void pruneReleaseHistory(String name, String namespace, int maxHistory) {
		timeVoid(deleteTimer, () -> delegate.pruneReleaseHistory(name, namespace, maxHistory));
	}

	@Override
	public void ensureNamespace(String namespace) {
		timeVoid(applyTimer, () -> delegate.ensureNamespace(namespace));
	}

	@Override
	public void apply(String namespace, String yamlContent) {
		timeVoid(applyTimer, () -> delegate.apply(namespace, yamlContent));
	}

	@Override
	public void delete(String namespace, String yamlContent) {
		timeVoid(deleteTimer, () -> delegate.delete(namespace, yamlContent));
	}

	@Override
	public List<ResourceStatus> getResourceStatuses(String namespace, String manifest) {
		return time(getTimer, () -> delegate.getResourceStatuses(namespace, manifest));
	}

	@Override
	public void waitForReady(String namespace, String manifest, int timeoutSeconds) {
		delegate.waitForReady(namespace, manifest, timeoutSeconds);
	}

	private <T> T time(Timer timer, Supplier<T> supplier) {
		long startNanos = System.nanoTime();
		try {
			T result = supplier.get();
			successCounter.increment();
			return result;
		}
		catch (RuntimeException ex) {
			errorCounter.increment();
			throw ex;
		}
		finally {
			timer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
		}
	}

	private void timeVoid(Timer timer, Runnable runnable) {
		long startNanos = System.nanoTime();
		try {
			runnable.run();
			successCounter.increment();
		}
		catch (RuntimeException ex) {
			errorCounter.increment();
			throw ex;
		}
		finally {
			timer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
		}
	}

}
