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

    @Bean
    public CreateAction createAction() {
        return new CreateAction();
    }

    @Bean
    public TemplateAction templateAction(Engine engine) {
        return new TemplateAction(engine);
    }

    @Bean
    public StatusAction statusAction(KubeService kubeService) {
        return new StatusAction(kubeService);
    }

    @Bean
    public HistoryAction historyAction(KubeService kubeService) {
        return new HistoryAction(kubeService);
    }

    @Bean
    public RollbackAction rollbackAction(KubeService kubeService) {
        return new RollbackAction(kubeService);
    }

    @Bean
    public GetAction getAction(KubeService kubeService) {
        return new GetAction(kubeService);
    }

    @Bean
    public ShowAction showAction(ChartLoader chartLoader) {
        return new ShowAction(chartLoader);
    }

    @Bean
    public ChartLoader chartLoader() {
        return new ChartLoader();
    }
}
