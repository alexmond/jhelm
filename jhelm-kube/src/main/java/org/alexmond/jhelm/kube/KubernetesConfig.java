package org.alexmond.jhelm.kube;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class KubernetesConfig {

    @Bean
    public ApiClient apiClient() throws IOException {
        // This will load the configuration from ~/.kube/config or service account if in cluster
        return Config.defaultClient();
    }
}
