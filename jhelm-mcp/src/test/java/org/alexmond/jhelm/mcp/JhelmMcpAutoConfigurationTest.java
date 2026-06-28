package org.alexmond.jhelm.mcp;

import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.mcp.tools.ReleaseMutatingTools;
import org.alexmond.jhelm.mcp.tools.ReleaseReadTools;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the MCP auto-configuration gates the cluster-mutating release tools
 * behind the unified deny-by-default security policy: they are registered only when
 * {@code jhelm.security.mode=FULL} <em>and</em> {@code jhelm.security.api-key} is set,
 * while the read-only release tools are always registered. The required action beans are
 * supplied as Mockito mocks so the {@code @ConditionalOnBean} conditions are satisfied
 * without a live cluster.
 */
class JhelmMcpAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JhelmMcpAutoConfiguration.class))
		.withUserConfiguration(MockActionsConfiguration.class);

	@Test
	void defaultRegistersReadToolsButHidesMutatingTools() {
		this.contextRunner.run((context) -> {
			assertTrue(context.containsBean("jhelmReleaseReadTools"));
			context.getBean(ReleaseReadTools.class);
			assertFalse(context.containsBean("jhelmReleaseMutatingTools"),
					"mutating tools must not be registered by default (READ_ONLY, no key)");
		});
	}

	@Test
	void fullModeWithoutApiKeyHidesMutatingTools() {
		this.contextRunner.withPropertyValues("jhelm.security.mode=FULL").run((context) -> {
			assertTrue(context.containsBean("jhelmReleaseReadTools"));
			assertFalse(context.containsBean("jhelmReleaseMutatingTools"),
					"deny-by-default: FULL without an api-key must not register mutating tools");
		});
	}

	@Test
	void apiKeyWithoutFullModeHidesMutatingTools() {
		this.contextRunner.withPropertyValues("jhelm.security.api-key=secret")
			.run((context) -> assertFalse(context.containsBean("jhelmReleaseMutatingTools"),
					"a key alone (READ_ONLY) must not register mutating tools"));
	}

	@Test
	void fullModeWithApiKeyRegistersMutatingTools() {
		this.contextRunner.withPropertyValues("jhelm.security.mode=FULL", "jhelm.security.api-key=secret")
			.run((context) -> {
				assertTrue(context.containsBean("jhelmReleaseReadTools"));
				assertTrue(context.containsBean("jhelmReleaseMutatingTools"));
				context.getBean(ReleaseMutatingTools.class);
			});
	}

	/**
	 * Supplies the action beans the MCP tool conditions depend on, as Mockito mocks (no
	 * live cluster required).
	 */
	@Configuration
	static class MockActionsConfiguration {

		@Bean
		ListAction listAction() {
			return Mockito.mock(ListAction.class);
		}

		@Bean
		StatusAction statusAction() {
			return Mockito.mock(StatusAction.class);
		}

		@Bean
		GetAction getAction() {
			return Mockito.mock(GetAction.class);
		}

		@Bean
		HistoryAction historyAction() {
			return Mockito.mock(HistoryAction.class);
		}

		@Bean
		InstallAction installAction() {
			return Mockito.mock(InstallAction.class);
		}

		@Bean
		UpgradeAction upgradeAction() {
			return Mockito.mock(UpgradeAction.class);
		}

		@Bean
		UninstallAction uninstallAction() {
			return Mockito.mock(UninstallAction.class);
		}

		@Bean
		RollbackAction rollbackAction() {
			return Mockito.mock(RollbackAction.class);
		}

		@Bean
		TestAction testAction() {
			return Mockito.mock(TestAction.class);
		}

		@Bean
		ChartLoader chartLoader() {
			return Mockito.mock(ChartLoader.class);
		}

	}

}
