package org.alexmond.jhelm.rest;

import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.SearchHubAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.InstallAction;

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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration for the jhelm REST API module. Activates only when the application
 * is a web application (servlet-based). Runs after {@link JhelmCoreAutoConfiguration} so
 * that all core beans (actions, services) are available for REST controllers.
 *
 * <p>
 * <strong>Security:</strong> the jhelm REST API exposes cluster-mutating operations
 * (install, upgrade, uninstall, rollback) and ships with <strong>no
 * authentication</strong>. Securing it is the responsibility of the embedding application
 * (for example via Spring Security); it must never be exposed unauthenticated to an
 * untrusted network.
 */
@Slf4j
@AutoConfiguration(after = JhelmCoreAutoConfiguration.class)
@ConditionalOnWebApplication
@EnableConfigurationProperties(JhelmRestProperties.class)
public class JhelmRestAutoConfiguration {

	/**
	 * Logs a single startup warning that the REST API is unauthenticated, reminding
	 * operators to secure it (for example with Spring Security) in the consuming
	 * application.
	 */
	@PostConstruct
	public void warnUnauthenticated() {
		log.warn("jhelm REST API is enabled with NO authentication and exposes cluster-mutating "
				+ "operations; secure it (e.g. set up Spring Security) in your application before "
				+ "exposing it to any untrusted network.");
	}

	/**
	 * Registers the global REST exception handler unless one is already defined.
	 * @return the exception handler bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public JhelmRestExceptionHandler jhelmRestExceptionHandler() {
		return new JhelmRestExceptionHandler();
	}

	/**
	 * Registers the release controller when the required release actions are present.
	 * @param listAction lists releases
	 * @param statusAction reports release and resource status
	 * @param getAction reads release values, manifest and notes
	 * @param historyAction lists release revisions
	 * @param installAction installs releases
	 * @param upgradeAction upgrades releases
	 * @param uninstallAction uninstalls releases
	 * @param rollbackAction rolls releases back
	 * @param testAction runs release test hooks
	 * @param chartLoader loads charts for install and upgrade
	 * @param repoManager pulls charts from repositories
	 * @param properties REST module configuration
	 * @return the release controller bean
	 */
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

	/**
	 * Registers the chart controller when the required chart actions are present.
	 * @param templateAction renders chart templates
	 * @param createAction scaffolds new charts
	 * @param showAction exposes chart information
	 * @param repoManager pulls charts from repositories
	 * @param properties REST module configuration
	 * @return the chart controller bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ TemplateAction.class, RepoManager.class })
	public ChartController chartController(TemplateAction templateAction, CreateAction createAction,
			ShowAction showAction, RepoManager repoManager, JhelmRestProperties properties) {
		return new ChartController(templateAction, createAction, showAction, repoManager, properties);
	}

	/**
	 * Registers the repository controller when a {@link RepoManager} is present.
	 * @param repoManager manages chart repositories
	 * @param properties REST module configuration
	 * @return the repository controller bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(RepoManager.class)
	public RepoController repoController(RepoManager repoManager, JhelmRestProperties properties) {
		return new RepoController(repoManager, properties);
	}

	/**
	 * Registers the ArtifactHub controller when a {@link SearchHubAction} is present.
	 * @param searchHubAction performs ArtifactHub searches
	 * @return the hub controller bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(SearchHubAction.class)
	public HubController hubController(SearchHubAction searchHubAction) {
		return new HubController(searchHubAction);
	}

	/**
	 * Registers the dependency resolver when a {@link RepoManager} is present.
	 * @param repoManager pulls dependency charts from repositories
	 * @return the dependency resolver bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(RepoManager.class)
	public DependencyResolver dependencyResolver(RepoManager repoManager) {
		return new DependencyResolver(repoManager);
	}

	/**
	 * Registers the dependency controller when a {@link DependencyResolver} is present.
	 * @param dependencyResolver resolves dependency versions
	 * @param chartLoader loads the pulled chart
	 * @param repoManager pulls the chart from its repository
	 * @param properties REST module configuration
	 * @return the dependency controller bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(DependencyResolver.class)
	public DependencyController dependencyController(DependencyResolver dependencyResolver, ChartLoader chartLoader,
			RepoManager repoManager, JhelmRestProperties properties) {
		return new DependencyController(dependencyResolver, chartLoader, repoManager, properties);
	}

}
