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
    public InstallAction installAction(Engine engine) {
        return new InstallAction(engine);
    }

    @Bean
    public UpgradeAction upgradeAction(Engine engine) {
        return new UpgradeAction(engine);
    }
}
