package org.alexmond.jhelm.core.output;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Helm-shaped {@code -o json|yaml} output: {@code info} is a nested object
 * with snake_case keys (matching {@code helm status -o json}), and the JSON/YAML
 * serialize the nested structure rather than a Java object.
 */
class OutputFormatTest {

	private static Release sampleRelease() {
		ChartMetadata md = new ChartMetadata();
		md.setName("demo");
		md.setVersion("1.0.0");
		md.setAppVersion("2.1");
		Chart chart = new Chart();
		chart.setMetadata(md);
		Release.ReleaseInfo info = Release.ReleaseInfo.builder()
			.lastDeployed(OffsetDateTime.parse("2026-07-05T10:00:00Z"))
			.description("Install complete")
			.status(ReleaseStatus.DEPLOYED)
			.notes("NOTES: hi")
			.build();
		return Release.builder()
			.name("myrel")
			.namespace("default")
			.version(1)
			.chart(chart)
			.info(info)
			.manifest("apiVersion: v1\nkind: ConfigMap")
			.build();
	}

	@Test
	void testReleaseInfoIsANestedObjectWithSnakeCaseKeys() {
		Map<String, Object> map = OutputFormat.release(sampleRelease());

		// The top-level scalars.
		assertEquals("myrel", map.get("name"));
		assertEquals("default", map.get("namespace"));
		assertEquals(1, map.get("version"));

		// info is a nested map (serializes to a JSON object / YAML mapping), not a
		// scalar.
		Object infoValue = map.get("info");
		assertInstanceOf(Map.class, infoValue, "info must be a nested object");
		@SuppressWarnings("unchecked")
		Map<String, Object> info = (Map<String, Object>) infoValue;
		assertTrue(info.containsKey("first_deployed"), "snake_case first_deployed");
		assertTrue(info.containsKey("last_deployed"), "snake_case last_deployed");
		assertEquals("deployed", info.get("status"));
		assertEquals("Install complete", info.get("description"));
	}

	@Test
	void testReleaseJsonNestsInfoAsAnObject() {
		String json = OutputFormat.json(OutputFormat.release(sampleRelease()));
		// The nested object is real JSON, e.g.
		// "info":{"first_deployed":...,"status":"deployed"...}
		assertTrue(json.contains("\"info\":{"), "info renders as a JSON object, not a stringified POJO: " + json);
		assertTrue(json.contains("\"status\":\"deployed\""), json);
		assertTrue(json.contains("\"first_deployed\":"), json);
	}

	@Test
	void testReleaseYamlNestsInfoAsAMapping() {
		String yaml = OutputFormat.yaml(OutputFormat.release(sampleRelease()));
		assertTrue(yaml.contains("info:"), yaml);
		assertTrue(yaml.contains("  status: \"deployed\"") || yaml.contains("  status: deployed"), yaml);
	}

	@Test
	void testHistoryRowHelmShape() {
		Map<String, Object> row = OutputFormat.historyRow(sampleRelease());
		assertEquals(1, row.get("revision"));
		assertEquals("deployed", row.get("status"));
		assertEquals("demo-1.0.0", row.get("chart"));
		assertEquals("2.1", row.get("app_version"));
	}

	@Test
	void testListRowHelmShape() {
		Map<String, Object> row = OutputFormat.listRow(sampleRelease());
		assertEquals("myrel", row.get("name"));
		assertEquals("default", row.get("namespace"));
		assertEquals(1, row.get("revision"));
		assertEquals("deployed", row.get("status"));
		assertEquals("demo-1.0.0", row.get("chart"));
		assertEquals("2.1", row.get("app_version"));
		assertTrue(row.containsKey("updated"), "list row carries an updated timestamp");
	}

	@Test
	void testJsonIsAnArrayForRowLists() {
		String json = OutputFormat.json(List.of(OutputFormat.historyRow(sampleRelease())));
		assertTrue(json.startsWith("[") && json.endsWith("]"), json);
	}

}
