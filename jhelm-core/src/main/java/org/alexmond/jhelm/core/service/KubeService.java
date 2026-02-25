package org.alexmond.jhelm.core.service;

import java.util.List;
import java.util.Optional;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ResourceStatus;

public interface KubeService {

	void storeRelease(Release release) throws Exception;

	Optional<Release> getRelease(String name, String namespace) throws Exception;

	List<Release> listReleases(String namespace) throws Exception;

	List<Release> getReleaseHistory(String name, String namespace) throws Exception;

	void deleteReleaseHistory(String name, String namespace) throws Exception;

	void apply(String namespace, String yamlContent) throws Exception;

	void delete(String namespace, String yamlContent) throws Exception;

	/**
	 * Returns the readiness status of each Kubernetes resource described in the rendered
	 * manifest.
	 * @param namespace the namespace to query
	 * @param manifest rendered YAML manifest (may contain multiple documents)
	 * @return one {@link ResourceStatus} per resource found in the manifest
	 * @throws Exception if the Kubernetes API cannot be reached
	 */
	List<ResourceStatus> getResourceStatuses(String namespace, String manifest) throws Exception;

	/**
	 * Blocks until all resources described in the manifest are ready, or until the
	 * timeout elapses.
	 * @param namespace the namespace to query
	 * @param manifest rendered YAML manifest
	 * @param timeoutSeconds maximum seconds to wait
	 * @throws Exception if the timeout elapses before all resources are ready, or if the
	 * Kubernetes API cannot be reached
	 */
	void waitForReady(String namespace, String manifest, int timeoutSeconds) throws Exception;

}
