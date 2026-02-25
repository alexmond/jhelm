package org.alexmond.jhelm.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import org.alexmond.jhelm.core.action.CreateAction;
import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.InstallAction;
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

class JhelmCoreAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JhelmCoreAutoConfiguration.class));

	@Test
	void testCoreBeansRegisteredWithoutKubeService() {
		contextRunner.run((ctx) -> {
			assertNotNull(ctx.getBean(Engine.class));
			assertNotNull(ctx.getBean(ChartLoader.class));
			assertNotNull(ctx.getBean(RepoManager.class));
			assertNotNull(ctx.getBean(RegistryManager.class));
			assertNotNull(ctx.getBean(CreateAction.class));
			assertNotNull(ctx.getBean(TemplateAction.class));
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

}
