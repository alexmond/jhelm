package org.alexmond.jhelm.kube;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.kubernetes.client.openapi.ApiClient;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.gotemplate.helm.functions.KubernetesProvider;
import org.alexmond.jhelm.kube.service.internal.Fabric8KubernetesProvider;
import org.alexmond.jhelm.kube.service.internal.KubeClient;
import org.alexmond.jhelm.kube.service.internal.KubernetesClientProvider;

/**
 * Factory for building a cluster-backed {@link KubernetesProvider} (the
 * {@code lookup}/{@code kubeVersion} SPI) from a Kubernetes client the caller already
 * owns. This is the supported way for a host that manages its own clients (for example a
 * multi-cluster host holding one client per cluster) to obtain a provider without
 * depending on the auto-configuration or on any {@code ...service.internal} type.
 *
 * <p>
 * Pair this with the {@code KubeServiceResolver} multi-cluster routing seam (issue #773):
 * build one provider per cluster here alongside the matching {@link KubeService} from
 * {@link KubeServices}, so template {@code lookup} queries hit the right cluster.
 *
 * @see KubeServices
 */
public final class KubernetesProviders {

	private KubernetesProviders() {
	}

	/**
	 * Builds a {@link KubernetesProvider} backed by a Fabric8 client.
	 * @param client the Fabric8 Kubernetes client used to look up resources and query
	 * cluster version information
	 * @return the cluster-backed lookup provider
	 */
	public static KubernetesProvider fabric8(KubernetesClient client) {
		return new Fabric8KubernetesProvider(client);
	}

	/**
	 * Builds a {@link KubernetesProvider} backed by the official Kubernetes Java client.
	 * @param apiClient the official Kubernetes Java client used to look up resources and
	 * query cluster version information
	 * @return the cluster-backed lookup provider
	 */
	public static KubernetesProvider clientJava(ApiClient apiClient) {
		return new KubernetesClientProvider(new KubeClient(apiClient));
	}

}
