package org.alexmond.jhelm.kube;

import java.io.FileReader;
import java.io.IOException;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.core.KubeService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the jhelm Kubernetes integration module. Registers an
 * {@link ApiClient} and a {@link KubeService} implementation. Runs before
 * {@link JhelmCoreAutoConfiguration} so that the {@link KubeService} bean is available
 * for its {@code @ConditionalOnBean} checks.
 */
@AutoConfiguration(before = JhelmCoreAutoConfiguration.class)
@EnableConfigurationProperties(JhelmKubernetesProperties.class)
public class JhelmKubeAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ApiClient apiClient(JhelmKubernetesProperties props) throws IOException {
		if (props.getKubeconfigPath() != null) {
			return Config.fromConfig(KubeConfig.loadKubeConfig(new FileReader(props.getKubeconfigPath())));
		}
		return Config.defaultClient();
	}

	@Bean
	@ConditionalOnMissingBean(KubeService.class)
	public HelmKubeService helmKubeService(ApiClient apiClient) {
		return new HelmKubeService(apiClient);
	}

}
