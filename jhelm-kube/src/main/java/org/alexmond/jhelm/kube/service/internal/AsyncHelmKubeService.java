package org.alexmond.jhelm.kube.service.internal;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.AsyncKubeService;

/**
 * Async implementation of {@link AsyncKubeService}. Delegates all blocking Kubernetes API
 * calls to a virtual-thread executor, returning {@link CompletableFuture} instances that
 * complete when the underlying call finishes.
 * <p>
 * Virtual threads are created via {@link Executors#newVirtualThreadPerTaskExecutor()}
 * (Java 21), so each async call is backed by a lightweight virtual thread. The calling
 * platform thread is released immediately.
 * </p>
 */
@Slf4j
public class AsyncHelmKubeService extends HelmKubeService implements AsyncKubeService {

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	/**
	 * Creates an async service backed by the given {@link KubeClient}.
	 * @param kubeClient the jhelm Kubernetes client wrapper holding the configured API
	 * client used for all cluster operations
	 */
	public AsyncHelmKubeService(KubeClient kubeClient) {
		super(kubeClient);
	}

	@Override
	public CompletableFuture<Void> applyAsync(String namespace, String yamlContent) {
		return CompletableFuture.runAsync(() -> {
			try {
				apply(namespace, yamlContent);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}, executor);
	}

	@Override
	public CompletableFuture<Void> deleteAsync(String namespace, String yamlContent) {
		return CompletableFuture.runAsync(() -> {
			try {
				delete(namespace, yamlContent);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}, executor);
	}

	@Override
	public CompletableFuture<Void> storeReleaseAsync(Release release) {
		return CompletableFuture.runAsync(() -> {
			try {
				storeRelease(release);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}, executor);
	}

	@Override
	public CompletableFuture<Optional<Release>> getReleaseAsync(String name, String namespace) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return getRelease(name, namespace);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}, executor);
	}

	@Override
	public CompletableFuture<List<Release>> listReleasesAsync(String namespace) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return listReleases(namespace);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}, executor);
	}

	@Override
	public CompletableFuture<List<Release>> getReleaseHistoryAsync(String name, String namespace) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return getReleaseHistory(name, namespace);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}, executor);
	}

	@Override
	public CompletableFuture<Void> deleteReleaseHistoryAsync(String name, String namespace) {
		return CompletableFuture.runAsync(() -> {
			try {
				deleteReleaseHistory(name, namespace);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}, executor);
	}

	@Override
	public CompletableFuture<List<ResourceStatus>> getResourceStatusesAsync(String namespace, String manifest) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return getResourceStatuses(namespace, manifest);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}, executor);
	}

	@Override
	public CompletableFuture<Void> waitForReadyAsync(String namespace, String manifest, int timeoutSeconds) {
		return CompletableFuture.runAsync(() -> {
			try {
				waitForReady(namespace, manifest, timeoutSeconds);
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}, executor);
	}

}
