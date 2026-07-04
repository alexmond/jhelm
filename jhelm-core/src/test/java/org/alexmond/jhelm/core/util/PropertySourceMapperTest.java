package org.alexmond.jhelm.core.util;

import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.model.Environment;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses a Spring Cloud Config Server Environment JSON (shape captured from a live
 * server) and verifies {@link PropertySourceMapper}: activation-key stripping,
 * dotted/indexed un-flattening, and first-wins merge of {@code propertySources}.
 */
class PropertySourceMapperTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	// Highest-precedence first: sidecar file, then two multi-doc documents. Each document
	// still carries the activation key the server did not strip. Mixes dotted and
	// [i]-indexed
	// keys.
	private static final String ENV_JSON = """
			{
			  "name": "myapp",
			  "profiles": ["prod"],
			  "label": "main",
			  "version": "abc123",
			  "state": "",
			  "propertySources": [
			    {
			      "name": "myapp-prod.yml",
			      "source": {
			        "replicas": 5,
			        "image.tag": "prod",
			        "spring.config.activate.on-profile": "prod"
			      }
			    },
			    {
			      "name": "myapp.yml (document #1)",
			      "source": {
			        "replicas": 3,
			        "spring.config.activate.on-profile": "prod"
			      }
			    },
			    {
			      "name": "myapp.yml (document #0)",
			      "source": {
			        "replicas": 1,
			        "image.tag": "base",
			        "ports[0]": 8080,
			        "ports[1]": 8443,
			        "servers[0].name": "a",
			        "servers[0].port": 1,
			        "servers[1].name": "b"
			      }
			    }
			  ]
			}
			""";

	private Map<String, Object> mapped() {
		Environment env = MAPPER.readValue(ENV_JSON, Environment.class);
		return PropertySourceMapper.toValues(env);
	}

	@Test
	void testFirstSourceWins() {
		Map<String, Object> values = mapped();
		assertEquals(5, values.get("replicas"), "highest-precedence source (index 0) wins");
	}

	@Test
	void testHigherSourceOverridesNestedKey() {
		Map<String, Object> values = mapped();
		assertEquals("prod", ((Map<?, ?>) values.get("image")).get("tag"),
				"sidecar image.tag overrides the base document's");
	}

	@Test
	void testDottedKeysUnflattenToNestedMaps() {
		// image.tag came only as a flat dotted key; it must become {image: {tag: ...}}.
		Map<String, Object> values = mapped();
		assertTrue(values.get("image") instanceof Map, "dotted key un-flattens to a nested map");
	}

	@Test
	void testIndexedKeysUnflattenToLists() {
		Map<String, Object> values = mapped();
		assertEquals(List.of(8080, 8443), values.get("ports"), "ports[i] scalars become a list");

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> servers = (List<Map<String, Object>>) values.get("servers");
		assertEquals(2, servers.size(), "servers[i].* becomes a list of maps");
		assertEquals("a", servers.get(0).get("name"));
		assertEquals(1, servers.get(0).get("port"));
		assertEquals("b", servers.get(1).get("name"));
	}

	@Test
	void testActivationKeysStripped() {
		Map<String, Object> values = mapped();
		assertFalse(values.containsKey("spring"), "no spring.config.activate.* residue in .Values");
		assertFalse(values.toString().contains("on-profile"), "activation directive fully stripped");
	}

	@Test
	void testEmptyPropertySourcesYieldEmptyMap() {
		Environment env = MAPPER.readValue("""
				{ "name": "x", "profiles": ["default"], "propertySources": [] }
				""", Environment.class);
		assertTrue(PropertySourceMapper.toValues(env).isEmpty(), "no sources -> empty map");
	}

	@Test
	void testNullEnvironmentYieldsEmptyMap() {
		assertTrue(PropertySourceMapper.toValues(null).isEmpty());
	}

}
