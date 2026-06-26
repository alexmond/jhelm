package org.alexmond.jhelm.kube.service;

import io.kubernetes.client.openapi.ApiClient;

/**
 * jhelm-owned wrapper around the kubernetes-client {@link ApiClient}. It holds the
 * underlying {@code io.kubernetes.client.openapi.ApiClient} so that the concrete
 * kubernetes-client type stays off jhelm's public service signatures: the client's major
 * version is not pinned into jhelm's API, leaving jhelm free to upgrade the kubernetes
 * client without breaking its own public surface.
 *
 * <p>
 * Advanced users can still supply their own {@link ApiClient} bean (it is consumed as a
 * customization seam by the auto-configuration) and have it wrapped in a
 * {@code KubeClient}. The wrapped client is exposed only through a package-private
 * accessor, so the services in this package can unwrap it while external code cannot.
 */
public final class KubeClient {

	private final ApiClient apiClient;

	/**
	 * Creates a wrapper around the given Kubernetes API client.
	 * @param apiClient the kubernetes-client API client to wrap
	 */
	public KubeClient(ApiClient apiClient) {
		this.apiClient = apiClient;
	}

	/**
	 * Returns the wrapped Kubernetes API client. Package-private on purpose: only the
	 * services in this package may unwrap the client, keeping the concrete
	 * kubernetes-client type off jhelm's public API.
	 * @return the wrapped Kubernetes API client
	 */
	ApiClient apiClient() {
		return this.apiClient;
	}

}
