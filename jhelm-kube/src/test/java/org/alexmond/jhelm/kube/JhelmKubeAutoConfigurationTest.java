package org.alexmond.jhelm.kube;

import io.kubernetes.client.openapi.ApiClient;
import org.alexmond.jhelm.core.service.KubeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class JhelmKubeAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JhelmKubeAutoConfiguration.class));

	@Test
	void testApiClientAndKubeServiceRegistered() {
		contextRunner.run((ctx) -> {
			assertNotNull(ctx.getBean(ApiClient.class));
			assertNotNull(ctx.getBean(KubeService.class));
			assertNotNull(ctx.getBean(HelmKubeService.class));
		});
	}

	@Test
	void testConditionalOnMissingBeanKubeServiceAllowsOverride() {
		KubeService custom = mock(KubeService.class);
		contextRunner.withBean("customKubeService", KubeService.class, () -> custom)
			.run((ctx) -> assertNotNull(ctx.getBean(ApiClient.class)));
	}

}
