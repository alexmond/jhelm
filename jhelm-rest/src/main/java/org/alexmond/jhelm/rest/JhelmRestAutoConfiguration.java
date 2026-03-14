package org.alexmond.jhelm.rest;

import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.SearchHubAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.DependencyResolver;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.alexmond.jhelm.rest.controller.ChartController;
import org.alexmond.jhelm.rest.controller.DependencyController;
import org.alexmond.jhelm.rest.controller.HubController;
import org.alexmond.jhelm.rest.controller.ReleaseController;
import org.alexmond.jhelm.rest.controller.RepoController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the jhelm REST API module. Activates only when the application
 * is a web application (servlet-based). Runs after {@link JhelmCoreAutoConfiguration} so
 * that all core beans (actions, services) are available for REST controllers.
 */
@AutoConfiguration(after = JhelmCoreAutoConfiguration.class)
@ConditionalOnWebApplication
@EnableConfigurationProperties(JhelmRestProperties.class)
public class JhelmRestAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public JhelmRestExceptionHandler jhelmRestExceptionHandler() {
		return new JhelmRestExceptionHandler();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ ListAction.class, InstallAction.class, RepoManager.class })
	public ReleaseController releaseController(ListAction listAction, StatusAction statusAction, GetAction getAction,
			HistoryAction historyAction, InstallAction installAction, UpgradeAction upgradeAction,
			UninstallAction uninstallAction, RollbackAction rollbackAction, TestAction testAction,
			ChartLoader chartLoader, RepoManager repoManager, JhelmRestProperties properties) {
		return new ReleaseController(listAction, statusAction, getAction, historyAction, installAction, upgradeAction,
				uninstallAction, rollbackAction, testAction, chartLoader, repoManager, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ TemplateAction.class, RepoManager.class })
	public ChartController chartController(TemplateAction templateAction, LintAction lintAction,
			CreateAction createAction, ShowAction showAction, RepoManager repoManager, JhelmRestProperties properties) {
		return new ChartController(templateAction, lintAction, createAction, showAction, repoManager, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(RepoManager.class)
	public RepoController repoController(RepoManager repoManager, JhelmRestProperties properties) {
		return new RepoController(repoManager, properties);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(SearchHubAction.class)
	public HubController hubController(SearchHubAction searchHubAction) {
		return new HubController(searchHubAction);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(RepoManager.class)
	public DependencyResolver dependencyResolver(RepoManager repoManager) {
		return new DependencyResolver(repoManager);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(DependencyResolver.class)
	public DependencyController dependencyController(DependencyResolver dependencyResolver, ChartLoader chartLoader,
			RepoManager repoManager, JhelmRestProperties properties) {
		return new DependencyController(dependencyResolver, chartLoader, repoManager, properties);
	}

}
