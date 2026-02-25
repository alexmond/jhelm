package org.alexmond.jhelm.core.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.service.RegistryManager;
import org.alexmond.jhelm.core.service.RepoManager;

/**
 * Auto-configuration for the jhelm core module. Registers all core Helm beans. Beans that
 * require a {@link KubeService} are only created when one is present in the application
 * context.
 */
@AutoConfiguration
@EnableConfigurationProperties(JhelmCoreProperties.class)
public class JhelmCoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public RepoManager repoManager(JhelmCoreProperties props) {
		if (props.getConfigPath() != null) {
			RepoManager rm = new RepoManager(props.getConfigPath());
			rm.setInsecureSkipTlsVerify(props.isInsecureSkipTlsVerify());
			return rm;
		}
		RepoManager rm = new RepoManager();
		rm.setInsecureSkipTlsVerify(props.isInsecureSkipTlsVerify());
		return rm;
	}

	@Bean
	@ConditionalOnMissingBean
	public RegistryManager registryManager() {
		return new RegistryManager();
	}

	@Bean
	@ConditionalOnMissingBean
	public Engine engine() {
		return new Engine();
	}

	@Bean
	@ConditionalOnMissingBean
	public ChartLoader chartLoader() {
		return new ChartLoader();
	}

	@Bean
	@ConditionalOnMissingBean
	public CreateAction createAction() {
		return new CreateAction();
	}

	@Bean
	@ConditionalOnMissingBean
	public TemplateAction templateAction(Engine engine) {
		return new TemplateAction(engine);
	}

	@Bean
	@ConditionalOnMissingBean
	public ShowAction showAction(ChartLoader chartLoader) {
		return new ShowAction(chartLoader);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public InstallAction installAction(Engine engine, KubeService kubeService) {
		return new InstallAction(engine, kubeService);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public UpgradeAction upgradeAction(Engine engine, KubeService kubeService) {
		return new UpgradeAction(engine, kubeService);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public UninstallAction uninstallAction(KubeService kubeService) {
		return new UninstallAction(kubeService);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public ListAction listAction(KubeService kubeService) {
		return new ListAction(kubeService);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public StatusAction statusAction(KubeService kubeService) {
		return new StatusAction(kubeService);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public HistoryAction historyAction(KubeService kubeService) {
		return new HistoryAction(kubeService);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public RollbackAction rollbackAction(KubeService kubeService) {
		return new RollbackAction(kubeService);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public GetAction getAction(KubeService kubeService) {
		return new GetAction(kubeService);
	}

}
