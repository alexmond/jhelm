package org.alexmond.jhelm.mcp.tools;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.alexmond.jhelm.core.action.GetAction;
import org.alexmond.jhelm.core.action.HistoryAction;
import org.alexmond.jhelm.core.action.ListAction;
import org.alexmond.jhelm.core.action.StatusAction;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class ReleaseReadToolsTest {

	@Mock
	private ListAction listAction;

	@Mock
	private StatusAction statusAction;

	@Mock
	private GetAction getAction;

	@Mock
	private HistoryAction historyAction;

	private ReleaseReadTools tools;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.tools = new ReleaseReadTools(this.listAction, this.statusAction, this.getAction, this.historyAction);
	}

	private static Release sampleRelease() {
		ChartMetadata md = ChartMetadata.builder().name("nginx").version("1.0.0").appVersion("1.25").build();
		Chart chart = Chart.builder().metadata(md).build();
		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.status(ReleaseStatus.DEPLOYED)
			.description("Install complete")
			.lastDeployed(OffsetDateTime.parse("2026-07-05T10:00:00Z"))
			.build();
		return Release.builder().name("my-release").namespace("default").version(1).chart(chart).info(info).build();
	}

	@Test
	void listReturnsHelmShapedRows() {
		when(this.listAction.list("default")).thenReturn(List.of(sampleRelease()));

		List<Map<String, Object>> rows = this.tools.list("default");

		assertEquals(1, rows.size());
		assertEquals("my-release", rows.get(0).get("name"));
		assertEquals("nginx-1.0.0", rows.get(0).get("chart"));
		assertEquals(1, rows.get(0).get("revision"));
	}

	@Test
	void statusReturnsNestedInfoObject() {
		when(this.statusAction.status("my-release", "default")).thenReturn(Optional.of(sampleRelease()));

		Map<String, Object> result = this.tools.status("my-release", "default");

		assertEquals("my-release", result.get("name"));
		Object info = result.get("info");
		assertInstanceOf(Map.class, info);
		assertEquals("deployed", ((Map<?, ?>) info).get("status"));
	}

	@Test
	void statusNotFoundReturnsErrorObject() {
		when(this.statusAction.status("missing", "default")).thenReturn(Optional.empty());

		Map<String, Object> result = this.tools.status("missing", "default");

		assertTrue(String.valueOf(result.get("error")).contains("missing"), result.toString());
	}

	@Test
	void historyReturnsHelmShapedRows() {
		when(this.historyAction.history("my-release", "default")).thenReturn(List.of(sampleRelease()));

		List<Map<String, Object>> rows = this.tools.history("my-release", "default");

		assertEquals(1, rows.size());
		assertEquals(1, rows.get(0).get("revision"));
		assertEquals("1.25", rows.get(0).get("app_version"));
	}

}
