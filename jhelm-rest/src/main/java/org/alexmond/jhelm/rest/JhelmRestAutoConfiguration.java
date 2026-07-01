package org.alexmond.jhelm.rest;

import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
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
import org.alexmond.jhelm.rest.security.AccessModeInterceptor;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for the jhelm REST API module. Activates only when the application
 * is a web application (servlet-based). Runs after {@link JhelmCoreAutoConfiguration} so
 * that all core beans (actions, services) are available for REST controllers.
 *
 * <p>
 * <strong>Security:</strong> cluster-mutating endpoints are gated by the shared
 * {@code jhelm.security.*} policy — read-only by default, and mutating operations are
 * disabled unless {@code jhelm.security.mode=FULL} and a {@code jhelm.security.api-key}
 * are set (deny-by-default), then required as a header on each request. The built-in API
 * key is the floor; wire Spring Security in the application for real multi-user auth. The
 * startup security posture is logged by the core module.
 */
@AutoConfiguration(after = JhelmCoreAutoConfiguration.class,
		beforeName = "org.springframework.boot.servlet.autoconfigure.MultipartAutoConfiguration")
@ConditionalOnWebApplication
@EnableConfigurationProperties(JhelmRestProperties.class)
public class JhelmRestAutoConfiguration {

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
	 * Caps multipart uploads (chart archives) at
	 * {@link JhelmRestProperties#getMaxUploadSize()}, registered before Spring Boot's own
	 * so the module default applies unless the application defines its own
	 * {@link MultipartConfigElement}.
	 * @param properties REST module configuration providing the upload cap
	 * @return the multipart configuration bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public MultipartConfigElement multipartConfigElement(JhelmRestProperties properties) {
		MultipartConfigFactory factory = new MultipartConfigFactory();
		factory.setMaxFileSize(properties.getMaxUploadSize());
		factory.setMaxRequestSize(properties.getMaxUploadSize());
		return factory.createMultipartConfig();
	}

	/**
	 * Registers the access-mode interceptor that gates cluster-mutating endpoints behind
	 * the unified security policy (deny-by-default 403, then API-key 401).
	 * @param securityPolicy the unified security policy that gates mutating operations
	 * @return the access-mode interceptor bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public AccessModeInterceptor accessModeInterceptor(JhelmSecurityPolicy securityPolicy) {
		return new AccessModeInterceptor(securityPolicy);
	}

	/**
	 * Registers a {@link WebMvcConfigurer} that applies the {@link AccessModeInterceptor}
	 * to all REST paths.
	 * @param accessModeInterceptor the interceptor enforcing the access mode
	 * @return the web MVC configurer bean
	 */
	@Bean
	@ConditionalOnMissingBean(name = "jhelmAccessModeWebMvcConfigurer")
	public WebMvcConfigurer jhelmAccessModeWebMvcConfigurer(AccessModeInterceptor accessModeInterceptor) {
		return new WebMvcConfigurer() {
			@Override
			public void addInterceptors(InterceptorRegistry registry) {
				registry.addInterceptor(accessModeInterceptor);
			}
		};
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
