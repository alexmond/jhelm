package org.alexmond.jhelm.sample;

import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.service.KubeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Verifies that providing a mock {@link KubeService} bean causes all Kubernetes-dependent
 * actions to be auto-configured.
 */
@SpringBootTest
class KubeServiceWiringTest {

	@Autowired
	private InstallAction installAction;

	@Autowired
	private UpgradeAction upgradeAction;

	@Autowired
	private UninstallAction uninstallAction;

	@Autowired
	private ListAction listAction;

	@Autowired
	private StatusAction statusAction;

	@Autowired
	private HistoryAction historyAction;

	@Autowired
	private RollbackAction rollbackAction;

	@Autowired
	private GetAction getAction;

	@Test
	void kubeActionsAvailableWithMockKubeService() {
		assertNotNull(installAction, "InstallAction should be present when KubeService is available");
		assertNotNull(upgradeAction, "UpgradeAction should be present when KubeService is available");
		assertNotNull(uninstallAction, "UninstallAction should be present when KubeService is available");
		assertNotNull(listAction, "ListAction should be present when KubeService is available");
		assertNotNull(statusAction, "StatusAction should be present when KubeService is available");
		assertNotNull(historyAction, "HistoryAction should be present when KubeService is available");
		assertNotNull(rollbackAction, "RollbackAction should be present when KubeService is available");
		assertNotNull(getAction, "GetAction should be present when KubeService is available");
	}

	@TestConfiguration
	static class MockKubeConfig {

		@Bean
		KubeService kubeService() {
			return mock(KubeService.class);
		}

	}

}
