package org.alexmond.jhelm.rest;

import org.alexmond.jhelm.core.JhelmCoreAutoConfiguration;
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
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.alexmond.jhelm.rest.controller.ReleaseController;
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
	@ConditionalOnBean({ ListAction.class, InstallAction.class })
	public ReleaseController releaseController(ListAction listAction, StatusAction statusAction, GetAction getAction,
			HistoryAction historyAction, InstallAction installAction, UpgradeAction upgradeAction,
			UninstallAction uninstallAction, RollbackAction rollbackAction, TestAction testAction,
			ChartLoader chartLoader) {
		return new ReleaseController(listAction, statusAction, getAction, historyAction, installAction, upgradeAction,
				uninstallAction, rollbackAction, testAction, chartLoader);
	}

}
