package org.alexmond.jhelm.kube.service.internal;

import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.Test;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KubernetesHealthIndicatorTest {

	@Test
	void reportsDownWhenClusterUnreachable() {
		// point at a port with nothing listening → the /version probe fails fast
		ApiClient client = new ApiClient();
		client.setBasePath("http://127.0.0.1:1");
		client.setConnectTimeout(500);
		client.setReadTimeout(500);
		KubernetesHealthIndicator indicator = new KubernetesHealthIndicator(new KubeClient(client));

		Health health = indicator.health();

		assertEquals(Status.DOWN, health.getStatus());
	}

}
