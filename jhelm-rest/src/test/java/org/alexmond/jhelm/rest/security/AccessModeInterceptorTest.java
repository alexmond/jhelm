package org.alexmond.jhelm.rest.security;

import java.util.List;

import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.action.TestAction;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.config.JhelmAccessMode;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.rest.config.JhelmRestProperties;
import org.alexmond.jhelm.rest.controller.ReleaseController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@link AccessModeInterceptor} blocks {@link MutatingOperation} endpoints
 * in {@link JhelmAccessMode#READ_ONLY READ_ONLY} mode while leaving read endpoints (and
 * all endpoints in {@link JhelmAccessMode#FULL FULL} mode) untouched.
 */
class AccessModeInterceptorTest {

	private JhelmRestProperties properties;

	private ListAction listAction;

	private UninstallAction uninstallAction;

	private ReleaseController controller;

	@BeforeEach
	void setUp() throws Exception {
		this.properties = new JhelmRestProperties();
		this.listAction = mock(ListAction.class);
		StatusAction statusAction = mock(StatusAction.class);
		GetAction getAction = mock(GetAction.class);
		HistoryAction historyAction = mock(HistoryAction.class);
		InstallAction installAction = mock(InstallAction.class);
		UpgradeAction upgradeAction = mock(UpgradeAction.class);
		this.uninstallAction = mock(UninstallAction.class);
		RollbackAction rollbackAction = mock(RollbackAction.class);
		TestAction testAction = mock(TestAction.class);
		ChartLoader chartLoader = mock(ChartLoader.class);
		RepoManager repoManager = mock(RepoManager.class);
		when(this.listAction.list(anyString())).thenReturn(List.of());
		this.controller = new ReleaseController(this.listAction, statusAction, getAction, historyAction, installAction,
				upgradeAction, this.uninstallAction, rollbackAction, testAction, chartLoader, repoManager,
				this.properties);
	}

	private MockMvc mockMvc() {
		return MockMvcBuilders.standaloneSetup(this.controller)
			.addInterceptors(new AccessModeInterceptor(this.properties))
			.addPlaceholderValue("jhelm.rest.base-path", "/api/v1")
			.build();
	}

	@Test
	void fullModeAllowsMutatingEndpoint() throws Exception {
		this.properties.setMode(JhelmAccessMode.FULL);
		mockMvc().perform(delete("/api/v1/releases/my-release")).andExpect(status().isNoContent());
	}

	@Test
	void readOnlyModeBlocksMutatingEndpoint() throws Exception {
		this.properties.setMode(JhelmAccessMode.READ_ONLY);
		mockMvc().perform(delete("/api/v1/releases/my-release")).andExpect(status().isForbidden());
	}

	@Test
	void readOnlyModeAllowsReadEndpoint() throws Exception {
		this.properties.setMode(JhelmAccessMode.READ_ONLY);
		mockMvc().perform(get("/api/v1/releases")).andExpect(status().isOk());
	}

}
