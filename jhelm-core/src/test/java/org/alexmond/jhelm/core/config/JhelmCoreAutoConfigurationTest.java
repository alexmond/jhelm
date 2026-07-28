package org.alexmond.jhelm.core.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
import org.alexmond.jhelm.core.JhelmMetricsAutoConfiguration;
import org.alexmond.jhelm.core.cache.TemplateCache;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.SearchHubAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.DelegatingKubeService;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.service.KubeServiceResolver;
import org.alexmond.jhelm.core.service.RegistryManager;
import org.alexmond.jhelm.core.service.RepoManager;
import java.util.Map;

class JhelmCoreAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(JhelmMetricsAutoConfiguration.class, JhelmCoreAutoConfiguration.class));

	@Test
	void testCoreBeansRegisteredWithoutKubeService() {
		contextRunner.run((ctx) -> {
			assertNotNull(ctx.getBean(Engine.class));
			assertNotNull(ctx.getBean(ChartLoader.class));
			assertNotNull(ctx.getBean(RepoManager.class));
			assertNotNull(ctx.getBean(RegistryManager.class));
			assertNotNull(ctx.getBean(CreateAction.class));
			assertNotNull(ctx.getBean(TemplateAction.class));
			assertNotNull(ctx.getBean(LintAction.class));
			assertNotNull(ctx.getBean(SearchHubAction.class));
		});
	}

	@Test
	void testKubeServiceDependentBeansAbsentWithoutKubeService() {
		contextRunner.run((ctx) -> assertEquals(0, ctx.getBeanNamesForType(InstallAction.class).length));
	}

	@Test
	void testKubeServiceDependentBeansRegisteredWhenKubeServicePresent() {
		contextRunner.withBean(KubeService.class, () -> mock(KubeService.class)).run((ctx) -> {
			assertNotNull(ctx.getBean(InstallAction.class));
			assertNotNull(ctx.getBean(UpgradeAction.class));
			assertNotNull(ctx.getBean(UninstallAction.class));
			assertNotNull(ctx.getBean(ListAction.class));
			assertNotNull(ctx.getBean(StatusAction.class));
			assertNotNull(ctx.getBean(HistoryAction.class));
			assertNotNull(ctx.getBean(RollbackAction.class));
			assertNotNull(ctx.getBean(GetAction.class));
		});
	}

	@Test
	void testConditionalOnMissingBeanAllowsOverride() {
		Engine customEngine = new Engine();
		contextRunner.withBean(Engine.class, () -> customEngine).run((ctx) -> assertNotNull(ctx.getBean(Engine.class)));
	}

	@Test
	void testCanonicalConfigPathPropertyPassedToRepoManager() {
		contextRunner.withPropertyValues("jhelm.config-path=/tmp/canonical-repos.yaml").run((ctx) -> {
			RepoManager repoManager = ctx.getBean(RepoManager.class);
			assertEquals("/tmp/canonical-repos.yaml", repoManager.getConfigPath());
		});
	}

	@Test
	void testCoreAliasConfigPathBindsToRepoManager() {
		// jhelm.core.config-path is the natural guess (matching jhelm.rest /
		// jhelm.security
		// / jhelm.plugins) but binds at the jhelm root as jhelm.config-path; the relaxed
		// alias in JhelmCoreAutoConfiguration makes the guess work instead of silently
		// falling through to the operator's Helm location.
		contextRunner.withPropertyValues("jhelm.core.config-path=/tmp/alias-repos.yaml").run((ctx) -> {
			RepoManager repoManager = ctx.getBean(RepoManager.class);
			assertEquals("/tmp/alias-repos.yaml", repoManager.getConfigPath());
		});
	}

	@Test
	void testCanonicalConfigPathWinsOverCoreAlias() {
		contextRunner
			.withPropertyValues("jhelm.config-path=/tmp/canonical.yaml", "jhelm.core.config-path=/tmp/alias.yaml")
			.run((ctx) -> {
				RepoManager repoManager = ctx.getBean(RepoManager.class);
				assertEquals("/tmp/canonical.yaml", repoManager.getConfigPath());
			});
	}

	@Test
	void testCoreAliasRepositoryCachePathBindsToRepoManager() {
		contextRunner.withPropertyValues("jhelm.core.repository-cache-path=/tmp/alias-cache").run((ctx) -> {
			RepoManager repoManager = ctx.getBean(RepoManager.class);
			assertEquals("/tmp/alias-cache", repoManager.getRepositoryCachePath());
		});
	}

	@Test
	void templateCacheBeanRegisteredByDefault() {
		contextRunner.run((ctx) -> assertNotNull(ctx.getBean(TemplateCache.class)));
	}

	@Test
	void templateCacheBeanAbsentWhenDisabled() {
		contextRunner.withPropertyValues("jhelm.template-cache-enabled=false")
			.run((ctx) -> assertEquals(0, ctx.getBeanNamesForType(TemplateCache.class).length));
	}

	@Test
	void templateCacheMaxSizeApplied() {
		contextRunner.withPropertyValues("jhelm.template-cache-max-size=10").run((ctx) -> {
			TemplateCache cache = ctx.getBean(TemplateCache.class);
			assertNotNull(cache);
			// Populate beyond the max and verify oldest are evicted
			for (int i = 0; i < 12; i++) {
				cache.put("key" + i, Map.of());
			}
			assertEquals(10, cache.size());
		});
	}

	@Test
	void jhelmMetricsBeanCreatedWhenMeterRegistryPresent() {
		contextRunner.withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new).run((ctx) -> {
			assertNotNull(ctx.getBean(JhelmMetrics.class));
			assertNotNull(ctx.getBean(Engine.class));
		});
	}

	@Test
	void jhelmMetricsBeanAbsentWithoutMeterRegistry() {
		contextRunner.run((ctx) -> assertEquals(0, ctx.getBeanNamesForType(JhelmMetrics.class).length));
	}

	@Test
	void testDelegatingKubeServiceAbsentWithoutResolver() {
		// Default single-cluster behavior: with no resolver bean the delegating service
		// is
		// never created, so the KubeService the context resolves — and that the actions
		// inject — is the real singleton exactly as before.
		KubeService real = mock(KubeService.class);
		contextRunner.withBean("realKubeService", KubeService.class, () -> real).run((ctx) -> {
			assertEquals(0, ctx.getBeanNamesForType(DelegatingKubeService.class).length);
			assertSame(real, ctx.getBean(KubeService.class));
			assertNotNull(ctx.getBean(InstallAction.class));
		});
	}

	@Test
	void testResolverPresentRoutesActionsThroughDelegating() {
		// With a resolver bean present, the @Primary delegating KubeService wins
		// injection
		// wherever a single KubeService is required (the actions), and every call routes
		// through the host's resolver to the per-request cluster service.
		KubeService real = mock(KubeService.class);
		KubeService resolved = mock(KubeService.class);
		KubeServiceResolver resolver = () -> resolved;
		contextRunner.withBean("realKubeService", KubeService.class, () -> real)
			.withBean(KubeServiceResolver.class, () -> resolver)
			.run((ctx) -> {
				// The @Primary bean a by-type single lookup resolves — the same
				// resolution
				// Spring uses to inject KubeService into the actions — is the delegating
				// one.
				KubeService primary = ctx.getBean(KubeService.class);
				assertInstanceOf(DelegatingKubeService.class, primary);
				assertNotNull(ctx.getBean(InstallAction.class));
				// A call through the delegating service reaches the resolved service.
				primary.listAllReleases();
				verify(resolved).listAllReleases();
			});
	}

}
