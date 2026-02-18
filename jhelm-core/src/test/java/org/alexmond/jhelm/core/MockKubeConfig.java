package org.alexmond.jhelm.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

@Configuration
public class MockKubeConfig {

    @Bean
    public KubeService kubeService() {
        return mock(KubeService.class);
    }
}
