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
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.config.JhelmSecurityProperties;
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
 * Verifies that {@link AccessModeInterceptor} gates {@link MutatingOperation} endpoints
 * behind the unified {@link JhelmSecurityPolicy} under deny-by-default semantics:
 * <ul>
 * <li>READ_ONLY (or FULL without a key) mutating → {@code 403};</li>
 * <li>FULL + key with a valid {@code X-API-Key} → allowed;</li>
 * <li>FULL + key with a missing/wrong key → {@code 401};</li>
 * <li>read endpoints are always allowed.</li>
 * </ul>
 */
class AccessModeInterceptorTest {

	private static final String API_KEY = "test-key";

	private ListAction listAction;

	private UninstallAction uninstallAction;

	private ReleaseController controller;

	@BeforeEach
	void setUp() throws Exception {
		JhelmRestProperties properties = new JhelmRestProperties();
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
				upgradeAction, this.uninstallAction, rollbackAction, testAction, chartLoader, repoManager, properties);
	}

	private static JhelmSecurityPolicy policy(JhelmAccessMode mode, String apiKey) {
		JhelmSecurityProperties props = new JhelmSecurityProperties();
		props.setMode(mode);
		props.setApiKey(apiKey);
		return new JhelmSecurityPolicy(props);
	}

	private MockMvc mockMvc(JhelmSecurityPolicy policy) {
		return MockMvcBuilders.standaloneSetup(this.controller)
			.addInterceptors(new AccessModeInterceptor(policy))
			.addPlaceholderValue("jhelm.rest.base-path", "/api/v1")
			.build();
	}

	@Test
	void readOnlyModeBlocksMutatingEndpointWith403() throws Exception {
		mockMvc(policy(JhelmAccessMode.READ_ONLY, API_KEY)).perform(delete("/api/v1/releases/my-release"))
			.andExpect(status().isForbidden());
	}

	@Test
	void fullModeWithoutKeyBlocksMutatingEndpointWith403() throws Exception {
		mockMvc(policy(JhelmAccessMode.FULL, null)).perform(delete("/api/v1/releases/my-release"))
			.andExpect(status().isForbidden());
	}

	@Test
	void fullModeWithKeyAndValidHeaderAllowsMutatingEndpoint() throws Exception {
		mockMvc(policy(JhelmAccessMode.FULL, API_KEY))
			.perform(delete("/api/v1/releases/my-release").header("X-API-Key", API_KEY))
			.andExpect(status().isNoContent());
	}

	@Test
	void fullModeWithKeyAndMissingHeaderRejectsWith401() throws Exception {
		mockMvc(policy(JhelmAccessMode.FULL, API_KEY)).perform(delete("/api/v1/releases/my-release"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void fullModeWithKeyAndWrongHeaderRejectsWith401() throws Exception {
		mockMvc(policy(JhelmAccessMode.FULL, API_KEY))
			.perform(delete("/api/v1/releases/my-release").header("X-API-Key", "wrong"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void readEndpointAlwaysAllowed() throws Exception {
		mockMvc(policy(JhelmAccessMode.READ_ONLY, null)).perform(get("/api/v1/releases")).andExpect(status().isOk());
	}

}
