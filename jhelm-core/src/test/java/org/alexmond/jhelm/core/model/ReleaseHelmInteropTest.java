package org.alexmond.jhelm.core.model;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the on-cluster release payload to Helm's JSON schema so `helm` and `jhelm` can
 * read each other's releases (the payload inside the {@code sh.helm.release.v1.*} Secret,
 * not just the Secret envelope). Uses the same mapper configuration as the storage layer.
 */
class ReleaseHelmInteropTest {

	// Same configuration as the storage layer (HelmKubeService).
	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void serializesInfoTimestampsAsHelmSnakeCase() {
		String json = this.mapper.writeValueAsString(sampleRelease());
		assertTrue(json.contains("\"first_deployed\""), json);
		assertTrue(json.contains("\"last_deployed\""), json);
		assertFalse(json.contains("firstDeployed"), json);
		assertFalse(json.contains("lastDeployed"), json);
	}

	@Test
	void serializesConfigAsABareMapLikeHelm() {
		ObjectNode root = (ObjectNode) this.mapper.readTree(this.mapper.writeValueAsString(sampleRelease()));
		// Helm stores user values directly under "config", not wrapped in a "values"
		// object
		assertEquals(2, root.get("config").get("replicaCount").asInt());
		assertFalse(root.get("config").has("values"), root.get("config").toString());
	}

	@Test
	void omitsNullFieldsLikeHelmOmitempty() {
		String json = this.mapper.writeValueAsString(sampleRelease());
		assertFalse(json.contains("null"), json);
	}

	@Test
	void serializesStatusAsHelmWireString() {
		String json = this.mapper.writeValueAsString(sampleRelease());
		assertTrue(json.contains("\"status\":\"deployed\""), json);
	}

	@Test
	void readsAHelmShapedReleaseIncludingUnknownFields() {
		// A payload as the Helm CLI writes it: snake_case info, bare config, and fields
		// jhelm
		// does not model (hooks, labels). jhelm must read it without throwing.
		String helmJson = """
				{
				  "name": "wordpress",
				  "namespace": "default",
				  "version": 3,
				  "info": {
				    "first_deployed": "2026-01-01T00:00:00Z",
				    "last_deployed": "2026-01-03T00:00:00Z",
				    "deleted": "",
				    "description": "Upgrade complete",
				    "status": "deployed",
				    "notes": "hello"
				  },
				  "config": { "replicaCount": 5, "image": { "tag": "1.2.3" } },
				  "manifest": "apiVersion: v1",
				  "hooks": [ { "name": "pre-install", "kind": "Job" } ],
				  "labels": { "owner": "helm" }
				}
				""";
		Release r = this.mapper.readValue(helmJson, Release.class);
		assertEquals("wordpress", r.getName());
		assertEquals(3, r.getVersion());
		assertNotNull(r.getInfo());
		assertEquals(OffsetDateTime.parse("2026-01-01T00:00:00Z"), r.getInfo().getFirstDeployed());
		assertEquals(OffsetDateTime.parse("2026-01-03T00:00:00Z"), r.getInfo().getLastDeployed());
		assertEquals(ReleaseStatus.DEPLOYED, r.getInfo().getStatus());
		assertNotNull(r.getConfig());
		assertEquals(5, r.getConfig().getValues().get("replicaCount"));
	}

	@Test
	void encodesChartTemplatesAndFilesInHelmFormat() {
		HelmReleaseCodec codec = new HelmReleaseCodec();
		ObjectNode root = (ObjectNode) this.mapper.readTree(codec.toJson(sampleReleaseWithChart()));
		ObjectNode chart = (ObjectNode) root.get("chart");
		// Helm stores chart file bytes base64-encoded
		String expectedTemplate = Base64.getEncoder()
			.encodeToString("apiVersion: apps/v1".getBytes(StandardCharsets.UTF_8));
		assertEquals(expectedTemplate, chart.get("templates").get(0).get("data").asString());
		// files: Helm uses an array of {name, data(base64)}, not jhelm's map
		assertTrue(chart.get("files").isArray(), chart.get("files").toString());
		assertEquals("NOTES.txt", chart.get("files").get(0).get("name").asString());
		// values schema: Helm's field is "schema" (base64), not "valuesSchema"
		assertFalse(chart.has("valuesSchema"), chart.toString());
		assertEquals(Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8)),
				chart.get("schema").asString());
	}

	@Test
	void roundTripsChartThroughHelmStorageFormat() {
		HelmReleaseCodec codec = new HelmReleaseCodec();
		Release back = codec.fromJson(codec.toJson(sampleReleaseWithChart()));
		Chart chart = back.getChart();
		assertNotNull(chart);
		// raw text is restored for the in-memory model the engine renders from
		assertEquals("apiVersion: apps/v1", chart.getTemplates().get(0).getData());
		assertEquals("templates/deployment.yaml", chart.getTemplates().get(0).getName());
		assertEquals("hello", chart.getFiles().get("NOTES.txt"));
		assertEquals("{}", chart.getValuesSchema());
	}

	@Test
	void readsAHelmShapedChartPayload() {
		// A chart embedded as the Helm CLI writes it: base64 template/file data, files as
		// an
		// array, schema base64. jhelm must decode it back to raw text.
		String rawTemplate = "kind: Deployment";
		String helmChartJson = """
				{
				  "name": "app", "namespace": "default", "version": 1,
				  "chart": {
				    "metadata": { "name": "app", "version": "1.0.0", "apiVersion": "v2" },
				    "templates": [ { "name": "templates/deployment.yaml", "data": "%s" } ],
				    "files": [ { "name": "README.md", "data": "%s" } ],
				    "schema": "%s"
				  }
				}
				""".formatted(b64(rawTemplate), b64("readme body"), b64("{\"type\":\"object\"}"));
		Release r = new HelmReleaseCodec().fromJson(helmChartJson.getBytes(StandardCharsets.UTF_8));
		assertEquals(rawTemplate, r.getChart().getTemplates().get(0).getData());
		assertEquals("readme body", r.getChart().getFiles().get("README.md"));
		assertEquals("{\"type\":\"object\"}", r.getChart().getValuesSchema());
	}

	private static String b64(String raw) {
		return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private Release sampleReleaseWithChart() {
		Map<String, String> files = new LinkedHashMap<>();
		files.put("NOTES.txt", "hello");
		Chart chart = Chart.builder()
			.metadata(ChartMetadata.builder().name("demo").version("1.0.0").apiVersion("v2").build())
			.templates(List
				.of(Chart.Template.builder().name("templates/deployment.yaml").data("apiVersion: apps/v1").build()))
			.values(Map.of("replicaCount", 2))
			.valuesSchema("{}")
			.files(files)
			.build();
		return sampleRelease().toBuilder().chart(chart).build();
	}

	private Release sampleRelease() {
		return Release.builder()
			.name("demo")
			.namespace("default")
			.version(1)
			.manifest("apiVersion: v1")
			.config(Release.MapConfig.builder().values(Map.of("replicaCount", 2)).build())
			.info(Release.ReleaseInfo.builder()
				.firstDeployed(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
				.lastDeployed(OffsetDateTime.parse("2026-01-02T00:00:00Z"))
				.status(ReleaseStatus.DEPLOYED)
				.description("Install complete")
				.build())
			.build();
	}

}
