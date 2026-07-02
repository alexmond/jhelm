package org.alexmond.jhelm.core.service;

import java.util.List;
import java.util.Optional;
import org.alexmond.jhelm.core.exception.KubernetesOperationException;
import org.alexmond.jhelm.core.exception.ReleaseStorageException;
import org.alexmond.jhelm.core.exception.WaitTimeoutException;
import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;

/**
 * Abstraction over the Kubernetes operations Helm needs: storing and reading releases,
 * applying and deleting rendered manifests, and checking/waiting on resource readiness.
 * <p>
 * All failures are reported through the unchecked
 * {@link org.alexmond.jhelm.core.exception.JhelmException} hierarchy (chiefly
 * {@link KubernetesOperationException}), so callers are never forced to declare or catch
 * a checked {@code Exception}.
 */
public interface KubeService {

	/**
	 * Stores a release, matching Helm's {@code sh.helm.release.v1.*} Secret format.
	 * @param release the release to persist
	 * @throws ReleaseStorageException if the release cannot be encoded or written
	 */
	void storeRelease(Release release);

	/**
	 * Returns the latest revision of a release, if it exists.
	 * @param name the release name
	 * @param namespace the namespace to look in
	 * @return the latest release revision, or {@link Optional#empty()} if not found
	 * @throws KubernetesOperationException if the Kubernetes API cannot be reached
	 */
	Optional<Release> getRelease(String name, String namespace);

	/**
	 * Returns the latest revision of every release in a namespace.
	 * @param namespace the namespace to list
	 * @return the releases found, or an empty list if there are none
	 * @throws KubernetesOperationException if the Kubernetes API cannot be reached
	 */
	List<Release> listReleases(String namespace);

	/**
	 * Returns all stored revisions of a release, newest first.
	 * @param name the release name
	 * @param namespace the namespace to look in
	 * @return the release history, or an empty list if the release is unknown
	 * @throws KubernetesOperationException if the Kubernetes API cannot be reached
	 */
	List<Release> getReleaseHistory(String name, String namespace);

	/**
	 * Deletes every stored revision of a release.
	 * @param name the release name
	 * @param namespace the namespace to look in
	 * @throws KubernetesOperationException if the Kubernetes API cannot be reached
	 */
	void deleteReleaseHistory(String name, String namespace);

	/**
	 * Prunes a release's stored revision history, keeping only the newest
	 * {@code maxHistory} revisions and deleting the oldest ones. The current (highest
	 * version) revision is always retained. A {@code maxHistory} of {@code 0} or less
	 * means no limit, so the call is a no-op. Mirrors Helm's {@code --history-max}.
	 * @param name the release name
	 * @param namespace the namespace to look in
	 * @param maxHistory the maximum number of revisions to keep; {@code 0} or less means
	 * no limit
	 * @throws KubernetesOperationException if the Kubernetes API cannot be reached
	 */
	void pruneReleaseHistory(String name, String namespace, int maxHistory);

	/**
	 * Creates the given namespace if it does not already exist. A no-op if the namespace
	 * is already present. Mirrors Helm's {@code --create-namespace}.
	 * @param namespace the namespace to create
	 * @throws KubernetesOperationException if the namespace cannot be created
	 */
	void ensureNamespace(String namespace);

	/**
	 * Applies a rendered manifest to the cluster via server-side apply.
	 * @param namespace the target namespace
	 * @param yamlContent rendered YAML manifest (may contain multiple documents)
	 * @throws KubernetesOperationException if a resource cannot be applied
	 */
	void apply(String namespace, String yamlContent);

	/**
	 * Validates a rendered manifest against the cluster via a server-side dry-run apply
	 * (Helm {@code --dry-run=server}). The API server admits and validates the resources
	 * (defaulting, admission webhooks, quota) but persists nothing.
	 * <p>
	 * The default throws {@link UnsupportedOperationException} so that an implementation
	 * which does not support server-side dry-run fails loudly rather than silently
	 * applying the manifest for real. Decorators must forward this call to their
	 * delegate.
	 * @param namespace the target namespace
	 * @param yamlContent rendered YAML manifest (may contain multiple documents)
	 * @throws KubernetesOperationException if a resource fails server-side validation
	 * @throws UnsupportedOperationException if the implementation has no server-side
	 * dry-run support
	 */
	default void applyDryRun(String namespace, String yamlContent) {
		throw new UnsupportedOperationException(
				"server-side dry-run is not supported by this KubeService implementation");
	}

	/**
	 * Deletes the resources described in a rendered manifest.
	 * @param namespace the target namespace
	 * @param yamlContent rendered YAML manifest (may contain multiple documents)
	 * @throws KubernetesOperationException if a resource cannot be deleted
	 */
	void delete(String namespace, String yamlContent);

	/**
	 * Returns the readiness status of each Kubernetes resource described in the rendered
	 * manifest.
	 * @param namespace the namespace to query
	 * @param manifest rendered YAML manifest (may contain multiple documents)
	 * @return one {@link ResourceStatus} per resource found in the manifest
	 * @throws KubernetesOperationException if the manifest cannot be parsed or the
	 * Kubernetes API cannot be reached
	 */
	List<ResourceStatus> getResourceStatuses(String namespace, String manifest);

	/**
	 * Blocks until all resources described in the manifest are ready, or until the
	 * timeout elapses.
	 * @param namespace the namespace to query
	 * @param manifest rendered YAML manifest
	 * @param timeoutSeconds maximum seconds to wait
	 * @throws WaitTimeoutException if the timeout elapses before all resources are ready
	 * @throws KubernetesOperationException if the wait is interrupted or the Kubernetes
	 * API cannot be reached
	 */
	void waitForReady(String namespace, String manifest, int timeoutSeconds);

	/**
	 * Returns the {@code .Capabilities} to expose to templates for this cluster — chiefly
	 * the live server {@code KubeVersion}, so charts that gate on the Kubernetes version
	 * render against the real target instead of an engine default. The default
	 * implementation returns {@link Capabilities#DEFAULT} (engine built-ins), so an
	 * implementation that cannot reach a cluster, or a test double, still works.
	 * @return the capability override, never {@code null}
	 */
	default Capabilities getCapabilities() {
		return Capabilities.DEFAULT;
	}

}
