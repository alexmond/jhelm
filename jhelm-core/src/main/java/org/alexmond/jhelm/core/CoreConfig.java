package org.alexmond.jhelm.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfig {

    @Bean
    public RepoManager repoManager() {
        return new RepoManager();
    }

    @Bean
    public RegistryManager registryManager() {
        return new RegistryManager();
    }

    @Bean
    public Engine engine() {
        return new Engine();
    }

    @Bean
    public InstallAction installAction(Engine engine, KubeService kubeService) {
        return new InstallAction(engine, kubeService);
    }

    @Bean
    public UpgradeAction upgradeAction(Engine engine, KubeService kubeService) {
        return new UpgradeAction(engine, kubeService);
    }

    @Bean
    public UninstallAction uninstallAction(KubeService kubeService) {
        return new UninstallAction(kubeService);
    }

    @Bean
    public ListAction listAction(KubeService kubeService) {
        return new ListAction(kubeService);
    }
}
