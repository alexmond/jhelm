package org.alexmond.jhelm.kube.service;

import java.util.List;
import java.util.Optional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.KubeService;

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
	public void storeRelease(Release release) throws Exception {
		timeVoid(storeTimer, () -> delegate.storeRelease(release));
	}

	@Override
	public Optional<Release> getRelease(String name, String namespace) throws Exception {
		return time(getTimer, () -> delegate.getRelease(name, namespace));
	}

	@Override
	public List<Release> listReleases(String namespace) throws Exception {
		return time(listTimer, () -> delegate.listReleases(namespace));
	}

	@Override
	public List<Release> getReleaseHistory(String name, String namespace) throws Exception {
		return time(historyTimer, () -> delegate.getReleaseHistory(name, namespace));
	}

	@Override
	public void deleteReleaseHistory(String name, String namespace) throws Exception {
		timeVoid(deleteTimer, () -> delegate.deleteReleaseHistory(name, namespace));
	}

	@Override
	public void apply(String namespace, String yamlContent) throws Exception {
		timeVoid(applyTimer, () -> delegate.apply(namespace, yamlContent));
	}

	@Override
	public void delete(String namespace, String yamlContent) throws Exception {
		timeVoid(deleteTimer, () -> delegate.delete(namespace, yamlContent));
	}

	@Override
	public List<ResourceStatus> getResourceStatuses(String namespace, String manifest) throws Exception {
		return time(getTimer, () -> delegate.getResourceStatuses(namespace, manifest));
	}

	@Override
	public void waitForReady(String namespace, String manifest, int timeoutSeconds) throws Exception {
		delegate.waitForReady(namespace, manifest, timeoutSeconds);
	}

	private <T> T time(Timer timer, CheckedSupplier<T> supplier) throws Exception {
		long startNanos = System.nanoTime();
		try {
			T result = supplier.get();
			successCounter.increment();
			return result;
		}
		catch (Exception ex) {
			errorCounter.increment();
			throw ex;
		}
		finally {
			timer.record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
		}
	}

	private void timeVoid(Timer timer, CheckedRunnable runnable) throws Exception {
		long startNanos = System.nanoTime();
		try {
			runnable.run();
			successCounter.increment();
		}
		catch (Exception ex) {
			errorCounter.increment();
			throw ex;
		}
		finally {
			timer.record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
		}
	}

	@FunctionalInterface
	private interface CheckedSupplier<T> {

		T get() throws Exception;

	}

	@FunctionalInterface
	private interface CheckedRunnable {

		void run() throws Exception;

	}

}
