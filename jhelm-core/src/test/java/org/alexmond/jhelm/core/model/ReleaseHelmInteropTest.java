package org.alexmond.jhelm.core.model;

import java.time.OffsetDateTime;
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
