package org.alexmond.jhelm.core;

import java.util.List;

import org.alexmond.jhelm.core.cache.TemplateCache;
import org.alexmond.jhelm.core.config.ConfigServerProperties;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.config.JhelmCoreProperties;
import org.alexmond.jhelm.core.config.JhelmEncryptProperties;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.gotemplate.helm.functions.KubernetesProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.DependencyUpdateAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.PackageAction;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.SearchHubAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.action.VerifyAction;
import org.alexmond.jhelm.core.service.ChartDownloader;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.JhelmPostRendererAdapter;
import org.alexmond.jhelm.pluginapi.JhelmPlugins;
import org.alexmond.jhelm.pluginapi.JhelmPostRenderer;
import org.alexmond.jhelm.core.service.ConfigServerClient;
import org.alexmond.jhelm.core.service.ConfigServerValuesLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.ValueEncryptor;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.service.LifecycleListener;
import org.alexmond.jhelm.core.service.PostRenderProcessor;
import org.alexmond.jhelm.core.service.RegistryManager;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.service.SchemaValidator;
import org.alexmond.jhelm.core.service.SignatureService;
import org.apache.hc.client5.http.impl.classic.HttpClients;

/**
 * Auto-configuration for the jhelm core module. Registers all core Helm beans. Beans that
 * require a {@link KubeService} are only created when one is present in the application
 * context.
 */
@Slf4j
@AutoConfiguration(after = JhelmMetricsAutoConfiguration.class)
@EnableConfigurationProperties({ JhelmCoreProperties.class, JhelmSecurityProperties.class, ConfigServerProperties.class,
		JhelmEncryptProperties.class })
public class JhelmCoreAutoConfiguration {

	/**
	 * Provides the unified security policy derived from {@link JhelmSecurityProperties}.
	 * This single policy gates cluster-mutating operations across all jhelm adapters
	 * (REST, MCP) under deny-by-default semantics.
	 * @param props the unified security properties
	 * @return the resolved security policy bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public JhelmSecurityPolicy jhelmSecurityPolicy(JhelmSecurityProperties props) {
		return new JhelmSecurityPolicy(props);
	}

	/**
	 * Logs a single line describing the resolved security posture at startup, so
	 * operators can confirm whether mutating operations are enabled and protected.
	 * @param policy the resolved security policy
	 * @return an application runner that logs the posture once
	 */
	@Bean
	@ConditionalOnMissingBean(name = "jhelmSecurityPostureLogger")
	public ApplicationRunner jhelmSecurityPostureLogger(JhelmSecurityPolicy policy) {
		return (args) -> {
			if (policy.mutatingEnabled()) {
				log.info("jhelm security: FULL — mutating operations ENABLED, protected by an API key.");
			}
			else if (policy.mode() == JhelmAccessMode.FULL) {
				log.warn("jhelm security: FULL requested but no jhelm.security.api-key is set — mutating "
						+ "operations are DISABLED (deny-by-default). Set jhelm.security.api-key to enable them.");
			}
			else {
				log.info("jhelm security: READ_ONLY — mutating operations are disabled.");
			}
		};
	}

	/**
	 * Provides the chart repository manager, configured from the jhelm properties.
	 * @param props the jhelm core configuration properties
	 * @param registryManager the OCI registry auth provider
	 * @param metrics optional metrics for instrumenting chart pulls
	 * @return the repository manager bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public RepoManager repoManager(JhelmCoreProperties props, JhelmSecurityProperties securityProps,
			RegistryManager registryManager, ObjectProvider<JhelmMetrics> metrics,
			ObjectProvider<ChartDownloader> chartDownloaders) {
		boolean blockPrivate = securityProps.isBlockPrivateNetworks();
		RepoManager repoManager = (props.getConfigPath() != null)
				? new RepoManager(props.getConfigPath(), registryManager, props.isInsecureSkipTlsVerify(), blockPrivate)
				: new RepoManager(registryManager, props.isInsecureSkipTlsVerify(), blockPrivate);
		repoManager.setMetrics(metrics.getIfAvailable());
		repoManager.setRepositoryCacheOverride(props.getRepositoryCachePath());
		repoManager.setChartDownloaders(chartDownloaders.stream().toList());
		return repoManager;
	}

	/**
	 * Client that fetches chart values from a Spring Cloud Config Server over the shared
	 * SSRF-guarded HTTP path.
	 * @param repoManager provides the SSRF-guarded HTTP client factory
	 * @return the config-server client bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public ConfigServerClient configServerClient(RepoManager repoManager) {
		return new ConfigServerClient(repoManager);
	}

	/**
	 * Resolves and fetches config-server values (merging {@code jhelm.config-server.*}
	 * with per-command overrides), honoring fail-fast and the precedence flags. Disabled
	 * by default.
	 * @param configServerProperties the config-server configuration
	 * @param configServerClient the config-server client
	 * @return the config-server values loader bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public ConfigServerValuesLoader configServerValuesLoader(ConfigServerProperties configServerProperties,
			ConfigServerClient configServerClient) {
		return new ConfigServerValuesLoader(configServerProperties, configServerClient);
	}

	/**
	 * Decrypts {@code {cipher}} values in resolved chart values. A no-op unless
	 * {@code jhelm.encrypt.key} is set.
	 * @param encryptProperties the value-encryption configuration
	 * @return the value encryptor bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public ValueEncryptor valueEncryptor(JhelmEncryptProperties encryptProperties) {
		return new ValueEncryptor(encryptProperties.getKey(), encryptProperties.getSalt(),
				encryptProperties.isFailOnError());
	}

	/**
	 * Provides the OCI registry manager used for OCI-based charts. Honors
	 * {@code jhelm.registry-config-path}, then {@code $HELM_REGISTRY_CONFIG}, then the
	 * per-OS Helm default.
	 * @param props the jhelm core configuration properties
	 * @return the registry manager bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public RegistryManager registryManager(JhelmCoreProperties props) {
		if (props.getRegistryConfigPath() != null) {
			return new RegistryManager(props.getRegistryConfigPath());
		}
		return new RegistryManager();
	}

	/**
	 * Provides the template parse cache, enabled unless
	 * {@code jhelm.template-cache-enabled} is set to {@code false}.
	 * @param props the jhelm core configuration properties (supplies the max cache size)
	 * @param metrics optional metrics for recording cache statistics
	 * @return the template cache bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "jhelm.template-cache-enabled", havingValue = "true", matchIfMissing = true)
	public TemplateCache templateCache(JhelmCoreProperties props, ObjectProvider<JhelmMetrics> metrics) {
		return new TemplateCache(props.getTemplateCacheMaxSize(), metrics.getIfAvailable());
	}

	/**
	 * Provides the JSON-schema validator for chart values.
	 * @return the schema validator bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public SchemaValidator schemaValidator() {
		return new SchemaValidator();
	}

	/**
	 * Provides the template rendering engine wired with the cache, validator and metrics.
	 * When a {@link KubernetesProvider} is on the context (jhelm-kube present), it is
	 * wired in so the {@code lookup} template function queries the live cluster as Helm
	 * does; otherwise {@code lookup} falls back to the empty-map stub.
	 * @param templateCache optional template parse cache
	 * @param schemaValidator the values schema validator
	 * @param metrics optional metrics for instrumentation
	 * @param kubernetesProvider optional cluster-backed provider for the {@code lookup}
	 * function
	 * @return the engine bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public Engine engine(ObjectProvider<TemplateCache> templateCache, SchemaValidator schemaValidator,
			ObjectProvider<JhelmMetrics> metrics, ObjectProvider<KubernetesProvider> kubernetesProvider) {
		Engine engine = new Engine(templateCache.getIfAvailable(), schemaValidator, metrics.getIfAvailable());
		engine.setKubernetesProvider(kubernetesProvider.getIfAvailable());
		return engine;
	}

	/**
	 * Provides the chart loader that reads charts from disk.
	 * @return the chart loader bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public ChartLoader chartLoader() {
		return new ChartLoader();
	}

	/**
	 * Provides the {@code helm create} action for scaffolding new charts.
	 * @return the create action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public CreateAction createAction() {
		return new CreateAction();
	}

	/**
	 * Collects Java {@link JhelmPostRenderer} plugins — discovered as Spring beans and
	 * via {@link java.util.ServiceLoader} — as {@link PostRenderProcessor}s applied in
	 * the render path (install, upgrade, template) across every jhelm surface.
	 * @param postRendererPlugins the post-renderer plugin beans (if any)
	 * @return the post-render processors, or an empty list if no plugins are present
	 */
	@Bean
	@ConditionalOnMissingBean
	public List<PostRenderProcessor> jhelmPostRenderProcessors(ObjectProvider<JhelmPostRenderer> postRendererPlugins) {
		return JhelmPlugins.merge(JhelmPostRenderer.class, postRendererPlugins.stream().toList())
			.stream()
			.<PostRenderProcessor>map(JhelmPostRendererAdapter::new)
			.toList();
	}

	/**
	 * Provides the {@code helm template} action, wiring in any post-render processors.
	 * @param engine the rendering engine
	 * @param postRenderProcessors optional post-render processors applied to the manifest
	 * @return the template action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public TemplateAction templateAction(Engine engine, ChartLoader chartLoader,
			ObjectProvider<List<PostRenderProcessor>> postRenderProcessors, ValueEncryptor valueEncryptor) {
		TemplateAction action = new TemplateAction(engine, chartLoader);
		List<PostRenderProcessor> processors = postRenderProcessors.getIfAvailable();
		if (processors != null) {
			action.setPostRenderProcessors(processors);
		}
		action.setValueEncryptor(valueEncryptor);
		return action;
	}

	/**
	 * Provides the {@code helm dependency update} action, also used by the
	 * {@code --dependency-update} flag on
	 * {@code install}/{@code upgrade}/{@code template}.
	 * @param repoManager the repository manager used to refresh and download dependencies
	 * @return the dependency-update action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public DependencyUpdateAction dependencyUpdateAction(RepoManager repoManager) {
		return new DependencyUpdateAction(repoManager);
	}

	/**
	 * Provides the {@code helm show} action for inspecting chart metadata, values and
	 * README.
	 * @param chartLoader the chart loader
	 * @return the show action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public ShowAction showAction(ChartLoader chartLoader) {
		return new ShowAction(chartLoader);
	}

	/**
	 * Provides the {@code helm lint} action for validating charts.
	 * @param chartLoader the chart loader
	 * @param engine the rendering engine
	 * @param schemaValidator the values schema validator
	 * @return the lint action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public LintAction lintAction(ChartLoader chartLoader, Engine engine, SchemaValidator schemaValidator) {
		return new LintAction(chartLoader, engine, schemaValidator);
	}

	/**
	 * Provides the {@code helm install} action; only created when a {@link KubeService}
	 * is present.
	 * @param engine the rendering engine
	 * @param kubeService the cluster client
	 * @param postRenderProcessors optional post-render processors applied to the manifest
	 * @param lifecycleListeners optional listeners notified on install lifecycle events
	 * @return the install action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public InstallAction installAction(Engine engine, KubeService kubeService,
			ObjectProvider<List<PostRenderProcessor>> postRenderProcessors,
			ObjectProvider<List<LifecycleListener>> lifecycleListeners, ObjectProvider<JhelmMetrics> metrics,
			ValueEncryptor valueEncryptor) {
		InstallAction action = new InstallAction(engine, kubeService);
		List<PostRenderProcessor> processors = postRenderProcessors.getIfAvailable();
		if (processors != null) {
			action.setPostRenderProcessors(processors);
		}
		List<LifecycleListener> listeners = lifecycleListeners.getIfAvailable();
		if (listeners != null) {
			action.setLifecycleListeners(listeners);
		}
		action.setMetrics(metrics.getIfAvailable());
		action.setValueEncryptor(valueEncryptor);
		return action;
	}

	/**
	 * Provides the {@code helm upgrade} action; only created when a {@link KubeService}
	 * is present.
	 * @param engine the rendering engine
	 * @param kubeService the cluster client
	 * @param postRenderProcessors optional post-render processors applied to the manifest
	 * @param lifecycleListeners optional listeners notified on upgrade lifecycle events
	 * @return the upgrade action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public UpgradeAction upgradeAction(Engine engine, KubeService kubeService,
			ObjectProvider<List<PostRenderProcessor>> postRenderProcessors,
			ObjectProvider<List<LifecycleListener>> lifecycleListeners, ObjectProvider<JhelmMetrics> metrics,
			ValueEncryptor valueEncryptor) {
		UpgradeAction action = new UpgradeAction(engine, kubeService);
		List<PostRenderProcessor> processors = postRenderProcessors.getIfAvailable();
		if (processors != null) {
			action.setPostRenderProcessors(processors);
		}
		List<LifecycleListener> listeners = lifecycleListeners.getIfAvailable();
		if (listeners != null) {
			action.setLifecycleListeners(listeners);
		}
		action.setMetrics(metrics.getIfAvailable());
		action.setValueEncryptor(valueEncryptor);
		return action;
	}

	/**
	 * Provides the {@code helm uninstall} action; only created when a {@link KubeService}
	 * is present.
	 * @param kubeService the cluster client
	 * @param metrics optional metrics for instrumenting the action
	 * @return the uninstall action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public UninstallAction uninstallAction(KubeService kubeService, ObjectProvider<JhelmMetrics> metrics) {
		UninstallAction action = new UninstallAction(kubeService);
		action.setMetrics(metrics.getIfAvailable());
		return action;
	}

	/**
	 * Provides the {@code helm list} action; only created when a {@link KubeService} is
	 * present.
	 * @param kubeService the cluster client
	 * @return the list action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public ListAction listAction(KubeService kubeService) {
		return new ListAction(kubeService);
	}

	/**
	 * Provides the {@code helm status} action; only created when a {@link KubeService} is
	 * present.
	 * @param kubeService the cluster client
	 * @return the status action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public StatusAction statusAction(KubeService kubeService) {
		return new StatusAction(kubeService);
	}

	/**
	 * Provides the {@code helm history} action; only created when a {@link KubeService}
	 * is present.
	 * @param kubeService the cluster client
	 * @return the history action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public HistoryAction historyAction(KubeService kubeService) {
		return new HistoryAction(kubeService);
	}

	/**
	 * Provides the {@code helm rollback} action; only created when a {@link KubeService}
	 * is present.
	 * @param kubeService the cluster client
	 * @param metrics optional metrics for instrumenting the action
	 * @return the rollback action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public RollbackAction rollbackAction(KubeService kubeService, ObjectProvider<JhelmMetrics> metrics) {
		RollbackAction action = new RollbackAction(kubeService);
		action.setMetrics(metrics.getIfAvailable());
		return action;
	}

	/**
	 * Provides the {@code helm get} action; only created when a {@link KubeService} is
	 * present.
	 * @param kubeService the cluster client
	 * @return the get action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public GetAction getAction(KubeService kubeService) {
		return new GetAction(kubeService);
	}

	/**
	 * Provides the {@code helm test} action; only created when a {@link KubeService} is
	 * present.
	 * @param kubeService the cluster client
	 * @return the test action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(KubeService.class)
	public TestAction testAction(KubeService kubeService) {
		return new TestAction(kubeService);
	}

	/**
	 * Provides the PGP signature service used for signing and verifying chart packages.
	 * @return the signature service bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public SignatureService signatureService() {
		return new SignatureService();
	}

	/**
	 * Provides the {@code helm package} action for packaging charts into {@code .tgz}
	 * archives, optionally signed.
	 * @param chartLoader the chart loader
	 * @param signatureService the signature service used for optional signing
	 * @return the package action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public PackageAction packageAction(ChartLoader chartLoader, SignatureService signatureService) {
		return new PackageAction(chartLoader, signatureService);
	}

	/**
	 * Provides the {@code helm verify} action for verifying a packaged chart's signature.
	 * @param signatureService the signature service
	 * @return the verify action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public VerifyAction verifyAction(SignatureService signatureService) {
		return new VerifyAction(signatureService);
	}

	/**
	 * Provides the {@code helm search hub} action backed by a default HTTP client.
	 * @return the search-hub action bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public SearchHubAction searchHubAction() {
		return new SearchHubAction(HttpClients.createDefault());
	}

}
