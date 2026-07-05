package org.alexmond.jhelm.mcp.tools;

import java.util.List;

import org.alexmond.jhelm.core.action.LintAction;
import org.alexmond.jhelm.core.action.ShowAction;
import org.alexmond.jhelm.core.action.TemplateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ChartToolsTest {

	@Mock
	private TemplateAction templateAction;

	@Mock
	private ShowAction showAction;

	@Mock
	private LintAction lintAction;

	private ChartTools tools;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.tools = new ChartTools(this.templateAction, this.showAction, this.lintAction);
	}

	@Test
	void templatePassesRenderControls() {
		ArgumentCaptor<Boolean> isUpgrade = ArgumentCaptor.forClass(Boolean.class);
		ArgumentCaptor<Boolean> includeCrds = ArgumentCaptor.forClass(Boolean.class);
		ArgumentCaptor<Boolean> skipTests = ArgumentCaptor.forClass(Boolean.class);
		ArgumentCaptor<List<String>> showOnly = ArgumentCaptor.forClass(List.class);
		when(this.templateAction.renderWithControls(anyString(), anyString(), anyString(), anyMap(),
				isUpgrade.capture(), includeCrds.capture(), skipTests.capture(), showOnly.capture()))
			.thenReturn("kind: Deployment");

		String out = this.tools.template("/charts/nginx", "r", "default", List.of("templates/deployment.yaml"), true,
				true, true);

		assertEquals("kind: Deployment", out);
		assertEquals(true, isUpgrade.getValue());
		assertEquals(true, includeCrds.getValue());
		assertEquals(true, skipTests.getValue());
		assertEquals(List.of("templates/deployment.yaml"), showOnly.getValue());
	}

	@Test
	void templateDefaultsAreNoOp() {
		when(this.templateAction.renderWithControls(anyString(), anyString(), anyString(), anyMap(), anyBoolean(),
				anyBoolean(), anyBoolean(), any()))
			.thenReturn("kind: ConfigMap");

		String out = this.tools.template("/charts/nginx", "r", "default", null, false, false, false);

		assertEquals("kind: ConfigMap", out);
	}

}
