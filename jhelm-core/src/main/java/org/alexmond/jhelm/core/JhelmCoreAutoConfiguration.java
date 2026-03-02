package org.alexmond.jhelm.core;

import java.util.List;

import org.alexmond.jhelm.core.cache.TemplateCache;
import org.alexmond.jhelm.core.config.JhelmCoreProperties;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.PackageAction;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.action.VerifyAction;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.service.LifecycleListener;
import org.alexmond.jhelm.core.service.PostRenderProcessor;
import org.alexmond.jhelm.core.service.RegistryManager;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.service.SchemaValidator;
import org.alexmond.jhelm.core.service.SignatureService;

/**
 * Auto-configuration for the jhelm core module. Registers all core Helm beans. Beans that
 * require a {@link KubeService} are only created when one is present in the application
 * context.
 */
@AutoConfiguration(after = JhelmMetricsAutoConfiguration.class)
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
	@ConditionalOnProperty(name = "jhelm.template-cache-enabled", havingValue = "true", matchIfMissing = true)
	public TemplateCache templateCache(JhelmCoreProperties props, ObjectProvider<JhelmMetrics> metrics) {
		return new TemplateCache(props.getTemplateCacheMaxSize(), metrics.getIfAvailable());
	}

	@Bean
	@ConditionalOnMissingBean
	public SchemaValidator schemaValidator() {
		return new SchemaValidator();
	}

	@Bean
	@ConditionalOnMissingBean
	public Engine engine(ObjectProvider<TemplateCache> templateCache, SchemaValidator schemaValidator,
			ObjectProvider<JhelmMetrics> metrics) {
		return new Engine(templateCache.getIfAvailable(), schemaValidator, metrics.getIfAvailable());
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
	public TemplateAction templateAction(Engine engine,
			ObjectProvider<List<PostRenderProcessor>> postRenderProcessors) {
		TemplateAction action = new TemplateAction(engine);
		List<PostRenderProcessor> processors = postRenderProcessors.getIfAvailable();
		if (processors != null) {
			action.setPostRenderProcessors(processors);
		}
		return action;
	}

	@Bean
	@ConditionalOnMissingBean
	public ShowAction showAction(ChartLoader chartLoader) {
		return new ShowAction(chartLoader);
	}

	@Bean
	@ConditionalOnMissingBean
	public LintAction lintAction(ChartLoader chartLoader, Engine engine, SchemaValidator schemaValidator) {
		return new LintAction(chartLoader, engine, schemaValidator);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public InstallAction installAction(Engine engine, KubeService kubeService,
			ObjectProvider<List<PostRenderProcessor>> postRenderProcessors,
			ObjectProvider<List<LifecycleListener>> lifecycleListeners) {
		InstallAction action = new InstallAction(engine, kubeService);
		List<PostRenderProcessor> processors = postRenderProcessors.getIfAvailable();
		if (processors != null) {
			action.setPostRenderProcessors(processors);
		}
		List<LifecycleListener> listeners = lifecycleListeners.getIfAvailable();
		if (listeners != null) {
			action.setLifecycleListeners(listeners);
		}
		return action;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public UpgradeAction upgradeAction(Engine engine, KubeService kubeService,
			ObjectProvider<List<PostRenderProcessor>> postRenderProcessors,
			ObjectProvider<List<LifecycleListener>> lifecycleListeners) {
		UpgradeAction action = new UpgradeAction(engine, kubeService);
		List<PostRenderProcessor> processors = postRenderProcessors.getIfAvailable();
		if (processors != null) {
			action.setPostRenderProcessors(processors);
		}
		List<LifecycleListener> listeners = lifecycleListeners.getIfAvailable();
		if (listeners != null) {
			action.setLifecycleListeners(listeners);
		}
		return action;
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

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public TestAction testAction(KubeService kubeService) {
		return new TestAction(kubeService);
	}

	@Bean
	@ConditionalOnMissingBean
	public SignatureService signatureService() {
		return new SignatureService();
	}

	@Bean
	@ConditionalOnMissingBean
	public PackageAction packageAction(ChartLoader chartLoader, SignatureService signatureService) {
		return new PackageAction(chartLoader, signatureService);
	}

	@Bean
	@ConditionalOnMissingBean
	public VerifyAction verifyAction(SignatureService signatureService) {
		return new VerifyAction(signatureService);
	}

}
