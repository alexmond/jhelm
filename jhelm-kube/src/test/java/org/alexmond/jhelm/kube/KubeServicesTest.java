package org.alexmond.jhelm.kube;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.kubernetes.client.openapi.ApiClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.gotemplate.helm.functions.KubernetesProvider;
import org.alexmond.jhelm.kube.config.JhelmKubernetesProperties;
import org.alexmond.jhelm.kube.service.internal.AsyncHelmKubeService;
import org.alexmond.jhelm.kube.service.internal.Fabric8AsyncKubeService;
import org.alexmond.jhelm.kube.service.internal.Fabric8KubernetesProvider;
import org.alexmond.jhelm.kube.service.internal.KubernetesClientProvider;
import org.alexmond.jhelm.kube.service.internal.ObservableKubeService;
import org.alexmond.jhelm.kube.service.internal.RetryableKubeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

/**
 * Verifies the public {@link KubeServices} and {@link KubernetesProviders} factories
 * build the same decorator chain as the auto-configuration from a caller-supplied client,
 * using mocked clients (a live cluster is unavailable in unit tests).
 */
class KubeServicesTest {

	private static JhelmKubernetesProperties retryDisabled() {
		JhelmKubernetesProperties props = new JhelmKubernetesProperties();
		props.getRetry().setEnabled(false);
		return props;
	}

	@Test
	void fabric8IsRetryableByDefault() {
		KubeService service = KubeServices.fabric8(mock(KubernetesClient.class));
		assertInstanceOf(RetryableKubeService.class, service);
	}

	@Test
	void fabric8IsObservableWithMetrics() {
		JhelmMetrics metrics = new JhelmMetrics(new SimpleMeterRegistry());
		KubeService service = KubeServices.fabric8(mock(KubernetesClient.class), new JhelmKubernetesProperties(),
				metrics);
		assertInstanceOf(ObservableKubeService.class, service);
	}

	@Test
	void fabric8IsBareBaseWhenRetryDisabled() {
		KubeService service = KubeServices.fabric8(mock(KubernetesClient.class), retryDisabled(), null);
		assertInstanceOf(Fabric8AsyncKubeService.class, service);
	}

	@Test
	void clientJavaIsRetryableByDefault() {
		KubeService service = KubeServices.clientJava(mock(ApiClient.class));
		assertInstanceOf(RetryableKubeService.class, service);
	}

	@Test
	void clientJavaIsObservableWithMetrics() {
		JhelmMetrics metrics = new JhelmMetrics(new SimpleMeterRegistry());
		KubeService service = KubeServices.clientJava(mock(ApiClient.class), new JhelmKubernetesProperties(), metrics);
		assertInstanceOf(ObservableKubeService.class, service);
	}

	@Test
	void clientJavaIsBareBaseWhenRetryDisabled() {
		KubeService service = KubeServices.clientJava(mock(ApiClient.class), retryDisabled(), null);
		assertInstanceOf(AsyncHelmKubeService.class, service);
	}

	@Test
	void providersFabric8ReturnsFabric8Provider() {
		KubernetesProvider provider = KubernetesProviders.fabric8(mock(KubernetesClient.class));
		assertInstanceOf(Fabric8KubernetesProvider.class, provider);
	}

	@Test
	void providersClientJavaReturnsClientJavaProvider() {
		KubernetesProvider provider = KubernetesProviders.clientJava(mock(ApiClient.class));
		assertInstanceOf(KubernetesClientProvider.class, provider);
	}

}
