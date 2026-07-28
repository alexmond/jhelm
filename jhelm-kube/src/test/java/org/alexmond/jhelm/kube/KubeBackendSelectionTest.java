package org.alexmond.jhelm.kube;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.kubernetes.client.openapi.ApiClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.alexmond.jhelm.core.JhelmMetricsAutoConfiguration;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.gotemplate.helm.functions.KubernetesProvider;
import org.alexmond.jhelm.kube.service.internal.Fabric8AsyncKubeService;
import org.alexmond.jhelm.kube.service.internal.Fabric8KubernetesProvider;
import org.alexmond.jhelm.kube.service.internal.KubernetesClientProvider;
import org.alexmond.jhelm.kube.service.internal.ObservableKubeService;
import org.alexmond.jhelm.kube.service.internal.RetryableKubeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

/**
 * Verifies that the Kubernetes backend is selected deterministically from
 * {@code jhelm.kubernetes.backend} and the client libraries on the classpath. Both client
 * libraries are on this module's test classpath (each declared {@code optional}), so
 * these tests exercise the genuine both-present case.
 */
class KubeBackendSelectionTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(JhelmMetricsAutoConfiguration.class, JhelmKubeAutoConfiguration.class));

	@Test
	void autoPrefersClientJavaWhenBothOnClasspath() {
		this.contextRunner.withBean(ApiClient.class, () -> mock(ApiClient.class)).run((ctx) -> {
			assertInstanceOf(KubernetesClientProvider.class, ctx.getBean(KubernetesProvider.class));
			assertFalse(ctx.containsBean("kubernetesClient"),
					"Fabric8 client bean must not be created for client-java");
		});
	}

	@Test
	void explicitClientJavaSelectsClientJavaBackend() {
		this.contextRunner.withPropertyValues("jhelm.kubernetes.backend=client-java")
			.withBean(ApiClient.class, () -> mock(ApiClient.class))
			.run((ctx) -> assertInstanceOf(KubernetesClientProvider.class, ctx.getBean(KubernetesProvider.class)));
	}

	@Test
	void explicitFabric8SelectsFabric8Backend() {
		this.contextRunner.withPropertyValues("jhelm.kubernetes.backend=fabric8")
			.withBean(KubernetesClient.class, () -> mock(KubernetesClient.class))
			.run((ctx) -> {
				assertInstanceOf(Fabric8KubernetesProvider.class, ctx.getBean(KubernetesProvider.class));
				assertFalse(ctx.containsBean("kubeClient"), "client-java backend must be inactive when fabric8 chosen");
			});
	}

	@Test
	void fabric8BackendGetsTheSameDecoratorChain() {
		this.contextRunner.withPropertyValues("jhelm.kubernetes.backend=fabric8")
			.withBean(KubernetesClient.class, () -> mock(KubernetesClient.class))
			.run((ctx) -> assertInstanceOf(RetryableKubeService.class, ctx.getBean(KubeService.class)));
	}

	@Test
	void fabric8BackendIsObservableWhenMetricsAvailable() {
		this.contextRunner.withPropertyValues("jhelm.kubernetes.backend=fabric8")
			.withBean(KubernetesClient.class, () -> mock(KubernetesClient.class))
			.withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
			.run((ctx) -> assertInstanceOf(ObservableKubeService.class, ctx.getBean(KubeService.class)));
	}

	@Test
	void fabric8BackendBaseIsAsyncCapable() {
		this.contextRunner
			.withPropertyValues("jhelm.kubernetes.backend=fabric8", "jhelm.kubernetes.retry.enabled=false")
			.withBean(KubernetesClient.class, () -> mock(KubernetesClient.class))
			.run((ctx) -> assertInstanceOf(Fabric8AsyncKubeService.class, ctx.getBean(KubeService.class)));
	}

}
