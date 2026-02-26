package org.alexmond.jhelm.core.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;

/**
 * Async extension of {@link KubeService}. All methods return a {@link CompletableFuture}
 * that completes on a virtual-thread executor, freeing the calling thread while
 * Kubernetes API calls are in flight.
 * <p>
 * Exceptions thrown by the underlying synchronous calls are wrapped in
 * {@link java.util.concurrent.CompletionException} and can be retrieved via
 * {@link Throwable#getCause()}.
 * </p>
 */
public interface AsyncKubeService extends KubeService {

	/**
	 * Applies a YAML manifest asynchronously.
	 * @param namespace target namespace
	 * @param yamlContent rendered YAML manifest
	 * @return future that completes when all resources have been applied
	 */
	CompletableFuture<Void> applyAsync(String namespace, String yamlContent);

	/**
	 * Deletes resources in a YAML manifest asynchronously.
	 * @param namespace target namespace
	 * @param yamlContent rendered YAML manifest
	 * @return future that completes when all resources have been deleted
	 */
	CompletableFuture<Void> deleteAsync(String namespace, String yamlContent);

	/**
	 * Stores a release asynchronously.
	 * @param release the release to store
	 * @return future that completes when the release has been stored
	 */
	CompletableFuture<Void> storeReleaseAsync(Release release);

	/**
	 * Retrieves the latest version of a release asynchronously.
	 * @param name release name
	 * @param namespace target namespace
	 * @return future containing the release if found
	 */
	CompletableFuture<Optional<Release>> getReleaseAsync(String name, String namespace);

	/**
	 * Lists all releases in a namespace asynchronously.
	 * @param namespace target namespace
	 * @return future containing all releases
	 */
	CompletableFuture<List<Release>> listReleasesAsync(String namespace);

	/**
	 * Retrieves all versions of a release asynchronously.
	 * @param name release name
	 * @param namespace target namespace
	 * @return future containing the release history
	 */
	CompletableFuture<List<Release>> getReleaseHistoryAsync(String name, String namespace);

	/**
	 * Deletes all versions of a release asynchronously.
	 * @param name release name
	 * @param namespace target namespace
	 * @return future that completes when all release records have been deleted
	 */
	CompletableFuture<Void> deleteReleaseHistoryAsync(String name, String namespace);

	/**
	 * Returns resource readiness statuses asynchronously.
	 * @param namespace target namespace
	 * @param manifest rendered YAML manifest
	 * @return future containing one {@link ResourceStatus} per resource
	 */
	CompletableFuture<List<ResourceStatus>> getResourceStatusesAsync(String namespace, String manifest);

	/**
	 * Waits for all resources to become ready, asynchronously.
	 * @param namespace target namespace
	 * @param manifest rendered YAML manifest
	 * @param timeoutSeconds maximum seconds to wait
	 * @return future that completes when all resources are ready, or fails on timeout
	 */
	CompletableFuture<Void> waitForReadyAsync(String namespace, String manifest, int timeoutSeconds);

}
