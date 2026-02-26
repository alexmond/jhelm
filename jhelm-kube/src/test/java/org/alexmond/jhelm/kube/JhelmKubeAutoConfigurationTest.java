package org.alexmond.jhelm.kube;

import io.kubernetes.client.openapi.ApiClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.alexmond.jhelm.core.JhelmMetricsAutoConfiguration;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.kube.service.HelmKubeService;
import org.alexmond.jhelm.kube.service.ObservableKubeService;
import org.alexmond.jhelm.kube.service.RetryableKubeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class JhelmKubeAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(JhelmMetricsAutoConfiguration.class, JhelmKubeAutoConfiguration.class));

	@Test
	@KubeClusterAvailable
	void testKubeServiceIsRetryableByDefaultWithCluster() {
		contextRunner.run((ctx) -> {
			assertNotNull(ctx.getBean(ApiClient.class));
			KubeService kubeService = ctx.getBean(KubeService.class);
			assertInstanceOf(RetryableKubeService.class, kubeService);
		});
	}

	@Test
	void testKubeServiceIsRetryableByDefaultWithMockedClient() {
		ApiClient mockClient = mock(ApiClient.class);
		contextRunner.withBean(ApiClient.class, () -> mockClient).run((ctx) -> {
			assertNotNull(ctx.getBean(ApiClient.class));
			KubeService kubeService = ctx.getBean(KubeService.class);
			assertInstanceOf(RetryableKubeService.class, kubeService);
		});
	}

	@Test
	void testRetryDisabledReturnsBaseService() {
		ApiClient mockClient = mock(ApiClient.class);
		contextRunner.withBean(ApiClient.class, () -> mockClient)
			.withPropertyValues("jhelm.kubernetes.retry.enabled=false")
			.run((ctx) -> {
				KubeService kubeService = ctx.getBean(KubeService.class);
				assertNotNull(kubeService);
				assertInstanceOf(HelmKubeService.class, kubeService);
			});
	}

	@Test
	void testObservableKubeServiceWrappedWhenMetricsAvailable() {
		ApiClient mockClient = mock(ApiClient.class);
		contextRunner.withBean(ApiClient.class, () -> mockClient)
			.withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
			.run((ctx) -> {
				assertNotNull(ctx.getBean(JhelmMetrics.class));
				KubeService kubeService = ctx.getBean(KubeService.class);
				assertInstanceOf(ObservableKubeService.class, kubeService);
			});
	}

	@Test
	void testNoObservableWrapperWithoutMetrics() {
		ApiClient mockClient = mock(ApiClient.class);
		contextRunner.withBean(ApiClient.class, () -> mockClient).run((ctx) -> {
			KubeService kubeService = ctx.getBean(KubeService.class);
			// Without MeterRegistry, should be RetryableKubeService (no Observable
			// wrapper)
			assertInstanceOf(RetryableKubeService.class, kubeService);
		});
	}

	@Test
	void testConditionalOnMissingBeanKubeServiceAllowsOverride() {
		ApiClient mockClient = mock(ApiClient.class);
		KubeService custom = mock(KubeService.class);
		contextRunner.withBean(ApiClient.class, () -> mockClient)
			.withBean("customKubeService", KubeService.class, () -> custom)
			.run((ctx) -> assertNotNull(ctx.getBean(ApiClient.class)));
	}

}
