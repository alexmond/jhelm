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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
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
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.KubeService;
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
	void testConfigPathPropertyPassedToRepoManager() {
		contextRunner.withPropertyValues("jhelm.core.config-path=/tmp/test-repos.yaml")
			.run((ctx) -> assertNotNull(ctx.getBean(RepoManager.class)));
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

}
