package org.alexmond.jhelm.kube.service.internal;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;
import org.alexmond.jhelm.core.service.AsyncKubeService;

/**
 * Async implementation of {@link AsyncKubeService} backed by the Fabric8 client. Mirrors
 * {@link AsyncHelmKubeService}: each async method delegates its blocking call to a
 * virtual-thread executor ({@link Executors#newVirtualThreadPerTaskExecutor()}, Java 21),
 * returning a {@link CompletableFuture} that completes when the underlying synchronous
 * {@link Fabric8KubeService} call finishes.
 *
 * <p>
 * The synchronous delegates throw only the unchecked {@code JhelmException} hierarchy, so
 * a thrown exception surfaces as the cause of the future's {@code CompletionException},
 * preserving its exact type for {@code get()}/{@code join()} callers.
 */
@Slf4j
public class Fabric8AsyncKubeService extends Fabric8KubeService implements AsyncKubeService {

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	/**
	 * Creates an async service backed by the given Fabric8 client.
	 * @param client the configured Fabric8 Kubernetes client used for all cluster
	 * operations
	 */
	public Fabric8AsyncKubeService(KubernetesClient client) {
		super(client);
	}

	@Override
	public CompletableFuture<Void> applyAsync(String namespace, String yamlContent) {
		return CompletableFuture.runAsync(() -> apply(namespace, yamlContent), this.executor);
	}

	@Override
	public CompletableFuture<Void> deleteAsync(String namespace, String yamlContent) {
		return CompletableFuture.runAsync(() -> delete(namespace, yamlContent), this.executor);
	}

	@Override
	public CompletableFuture<Void> storeReleaseAsync(Release release) {
		return CompletableFuture.runAsync(() -> storeRelease(release), this.executor);
	}

	@Override
	public CompletableFuture<Optional<Release>> getReleaseAsync(String name, String namespace) {
		return CompletableFuture.supplyAsync(() -> getRelease(name, namespace), this.executor);
	}

	@Override
	public CompletableFuture<List<Release>> listReleasesAsync(String namespace) {
		return CompletableFuture.supplyAsync(() -> listReleases(namespace), this.executor);
	}

	@Override
	public CompletableFuture<List<Release>> getReleaseHistoryAsync(String name, String namespace) {
		return CompletableFuture.supplyAsync(() -> getReleaseHistory(name, namespace), this.executor);
	}

	@Override
	public CompletableFuture<Void> deleteReleaseHistoryAsync(String name, String namespace) {
		return CompletableFuture.runAsync(() -> deleteReleaseHistory(name, namespace), this.executor);
	}

	@Override
	public CompletableFuture<List<ResourceStatus>> getResourceStatusesAsync(String namespace, String manifest) {
		return CompletableFuture.supplyAsync(() -> getResourceStatuses(namespace, manifest), this.executor);
	}

	@Override
	public CompletableFuture<Void> waitForReadyAsync(String namespace, String manifest, int timeoutSeconds) {
		return CompletableFuture.runAsync(() -> waitForReady(namespace, manifest, timeoutSeconds), this.executor);
	}

}
