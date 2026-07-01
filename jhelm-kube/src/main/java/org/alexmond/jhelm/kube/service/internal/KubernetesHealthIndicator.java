package org.alexmond.jhelm.kube.service.internal;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.openapi.models.VersionInfo;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports Kubernetes API-server connectivity as a Spring Boot health contributor: it
 * queries the cluster {@code /version} endpoint (the cheapest authenticated round-trip)
 * and reports {@code UP} with the server version when reachable, {@code DOWN} otherwise.
 * Registered only when Spring Boot Actuator is on the classpath.
 */
public class KubernetesHealthIndicator implements HealthIndicator {

	private final KubeClient kubeClient;

	/**
	 * Creates the indicator.
	 * @param kubeClient the wrapper holding the Kubernetes API client to probe
	 */
	public KubernetesHealthIndicator(KubeClient kubeClient) {
		this.kubeClient = kubeClient;
	}

	@Override
	public Health health() {
		try {
			VersionInfo version = new VersionApi(this.kubeClient.apiClient()).getCode().execute();
			return Health.up().withDetail("version", version.getGitVersion()).build();
		}
		catch (ApiException | RuntimeException ex) {
			return Health.down(ex).build();
		}
	}

}
