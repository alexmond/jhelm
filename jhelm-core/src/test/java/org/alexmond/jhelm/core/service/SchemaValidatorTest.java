package org.alexmond.jhelm.core.service;

import java.util.List;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;

import org.alexmond.jhelm.core.exception.SchemaValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaValidatorTest {

	private SchemaValidator validator;

	@BeforeEach
	void setUp() {
		validator = new SchemaValidator();
	}

	@Test
	void validate_nullSchema_doesNothing() {
		assertDoesNotThrow(() -> validator.validate("test-chart", null, Map.of("foo", "bar")));
	}

	@Test
	void validate_blankSchema_doesNothing() {
		assertDoesNotThrow(() -> validator.validate("test-chart", "  ", Map.of("foo", "bar")));
	}

	@Test
	void validate_validValues_doesNotThrow() {
		String schema = """
				{
				  "type": "object",
				  "properties": {
				    "replicas": { "type": "integer" }
				  }
				}
				""";
		assertDoesNotThrow(() -> validator.validate("test-chart", schema, Map.of("replicas", 3)));
	}

	@Test
	void validate_typeViolation_throwsException() {
		String schema = """
				{
				  "type": "object",
				  "properties": {
				    "replicas": { "type": "integer" }
				  }
				}
				""";
		SchemaValidationException ex = assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("replicas", "not-an-integer")));
		assertFalse(ex.getValidationErrors().isEmpty());
		assertTrue(ex.getMessage().contains("test-chart"));
	}

	@Test
	void validate_missingRequiredField_throwsException() {
		String schema = """
				{
				  "type": "object",
				  "required": ["name"],
				  "properties": {
				    "name": { "type": "string" }
				  }
				}
				""";
		SchemaValidationException ex = assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of()));
		assertTrue(ex.getValidationErrors().stream().anyMatch((e) -> e.contains("name") && e.contains("required")));
	}

	@Test
	void validate_enumViolation_throwsException() {
		String schema = """
				{
				  "type": "object",
				  "properties": {
				    "color": { "enum": ["red", "green", "blue"] }
				  }
				}
				""";
		SchemaValidationException ex = assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("color", "yellow")));
		assertFalse(ex.getValidationErrors().isEmpty());
	}

	@Test
	void validate_minimumViolation_throwsException() {
		String schema = """
				{
				  "type": "object",
				  "properties": {
				    "replicas": { "type": "integer", "minimum": 1 }
				  }
				}
				""";
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("replicas", 0)));
	}

	@Test
	void validate_maximumViolation_throwsException() {
		String schema = """
				{
				  "type": "object",
				  "properties": {
				    "replicas": { "type": "integer", "maximum": 10 }
				  }
				}
				""";
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("replicas", 100)));
	}

	@Test
	void validate_patternViolation_throwsException() {
		String schema = """
				{
				  "type": "object",
				  "properties": {
				    "image": { "type": "string", "pattern": "^[a-z]+/[a-z]+" }
				  }
				}
				""";
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("image", "UPPERCASE/IMAGE")));
	}

	@Test
	void validate_malformedSchema_logsWarningOnly() {
		assertDoesNotThrow(() -> validator.validate("test-chart", "not valid json { {", Map.of("foo", "bar")));
	}

	@Test
	void validate_nestedObjectValidation_detectsViolation() {
		String schema = """
				{
				  "type": "object",
				  "properties": {
				    "image": {
				      "type": "object",
				      "properties": {
				        "tag": { "type": "string" }
				      }
				    }
				  }
				}
				""";
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("image", Map.of("tag", 123))));
	}

	@Test
	void validate_exceptionMessage_containsChartNameAndErrors() {
		String schema = """
				{ "type": "object", "required": ["name"] }
				""";
		SchemaValidationException ex = assertThrows(SchemaValidationException.class,
				() -> validator.validate("my-chart", schema, Map.of()));
		assertTrue(ex.getMessage().contains("my-chart"));
		assertNotNull(ex.getValidationErrors());
		assertFalse(ex.getValidationErrors().isEmpty());
	}

	// --- Full-spec keywords the hand-rolled validator used to ignore (#816) ---

	@Test
	void validate_additionalPropertiesFalse_rejectsUnknownKey() {
		String schema = """
				{
				  "type": "object",
				  "properties": { "name": { "type": "string" } },
				  "additionalProperties": false
				}
				""";
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("name", "ok", "surprise", "x")));
	}

	@Test
	void validate_unknownKeysAllowedByDefault_doesNotThrow() {
		// Helm parity: unknown keys are permitted unless additionalProperties is false.
		String schema = """
				{
				  "type": "object",
				  "properties": { "name": { "type": "string" } }
				}
				""";
		assertDoesNotThrow(() -> validator.validate("test-chart", schema, Map.of("name", "ok", "extra", "fine")));
	}

	@Test
	void validate_refToDefs_isResolved() {
		String schema = """
				{
				  "type": "object",
				  "properties": { "port": { "$ref": "#/$defs/portNumber" } },
				  "$defs": {
				    "portNumber": { "type": "integer", "minimum": 1, "maximum": 65535 }
				  }
				}
				""";
		assertDoesNotThrow(() -> validator.validate("test-chart", schema, Map.of("port", 8080)));
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("port", 70000)));
	}

	@Test
	void validate_oneOf_enforced() {
		String schema = """
				{
				  "type": "object",
				  "properties": {
				    "value": { "oneOf": [ { "type": "string" }, { "type": "integer" } ] }
				  }
				}
				""";
		assertDoesNotThrow(() -> validator.validate("test-chart", schema, Map.of("value", "a-string")));
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("value", true)));
	}

	@Test
	void validate_anyOf_enforced() {
		String schema = """
				{
				  "type": "object",
				  "properties": {
				    "size": { "anyOf": [ { "type": "string", "enum": ["small", "large"] }, { "type": "integer" } ] }
				  }
				}
				""";
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("size", "medium")));
	}

	@Test
	void validate_allOf_enforced() {
		String schema = """
				{
				  "type": "object",
				  "properties": {
				    "name": {
				      "allOf": [ { "type": "string", "minLength": 3 }, { "pattern": "^[a-z]" } ]
				    }
				  }
				}
				""";
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("name", "Ab")));
	}

	@Test
	void validate_ifThenElse_conditionalRequired() {
		String schema = """
				{
				  "type": "object",
				  "properties": { "env": { "type": "string" } },
				  "if":   { "properties": { "env": { "const": "prod" } } },
				  "then": { "required": ["replicas"] }
				}
				""";
		assertDoesNotThrow(() -> validator.validate("test-chart", schema, Map.of("env", "dev")));
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("env", "prod")));
	}

	@Test
	void validate_arrayItems_enforced() {
		String schema = """
				{
				  "type": "object",
				  "properties": {
				    "ports": { "type": "array", "items": { "type": "integer" } }
				  }
				}
				""";
		assertDoesNotThrow(() -> validator.validate("test-chart", schema, Map.of("ports", List.of(80, 443))));
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("ports", List.of(80, "https"))));
	}

	@Test
	void validate_const_enforced() {
		String schema = """
				{
				  "type": "object",
				  "properties": { "apiVersion": { "const": "v1" } }
				}
				""";
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("apiVersion", "v2")));
	}

	@Test
	void validate_exclusiveMinimum_enforced() {
		String schema = """
				{
				  "type": "object",
				  "properties": { "replicas": { "type": "integer", "exclusiveMinimum": 0 } }
				}
				""";
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("replicas", 0)));
	}

	@Test
	void validate_multipleOf_enforced() {
		String schema = """
				{
				  "type": "object",
				  "properties": { "port": { "type": "integer", "multipleOf": 5 } }
				}
				""";
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("port", 7)));
	}

	// --- Helm float64 semantics: values arrive as boxed doubles ---

	@Test
	void validate_wholeDoubleSatisfiesIntegerType() {
		// Helm loads values as float64, so a port/replica count arrives as a whole double
		// (8080.0). JSON Schema says integer matches a number with zero fractional part.
		String schema = """
				{
				  "type": "object",
				  "properties": { "port": { "type": "integer" } }
				}
				""";
		assertDoesNotThrow(() -> validator.validate("test-chart", schema, Map.of("port", 8080.0d)));
	}

	@Test
	void validate_fractionalDoubleFailsIntegerType() {
		String schema = """
				{
				  "type": "object",
				  "properties": { "port": { "type": "integer" } }
				}
				""";
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("port", 80.5d)));
	}

	// --- $schema-driven draft selection (Helm 3 Draft-07 charts) ---

	@Test
	void validate_draft07Schema_selectedByDollarSchema() {
		String schema = """
				{
				  "$schema": "http://json-schema.org/draft-07/schema#",
				  "type": "object",
				  "properties": { "replicas": { "type": "integer", "minimum": 1 } },
				  "required": ["replicas"]
				}
				""";
		assertDoesNotThrow(() -> validator.validate("test-chart", schema, Map.of("replicas", 2)));
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("test-chart", schema, Map.of("replicas", 0)));
	}

	@Test
	void validate_sameSchemaTwice_usesCacheAndStaysConsistent() {
		String schema = """
				{
				  "type": "object",
				  "properties": { "name": { "type": "string" } },
				  "required": ["name"]
				}
				""";
		assertDoesNotThrow(() -> validator.validate("test-chart", schema, Map.of("name", "ok")));
		// Second call hits the compiled-schema cache and must behave identically.
		assertThrows(SchemaValidationException.class, () -> validator.validate("test-chart", schema, Map.of()));
	}

	// --- #831: input limits on an untrusted chart schema ---

	@Test
	void schemaNestedBeyondJhelmsLimitIsRejected() {
		// 300 nested levels is ~600 JSON levels: BELOW Jackson's own 1000-deep default
		// and ABOVE jhelm's 200, so this is rejected only because of jhelm's tighter
		// limit. Defence in depth — no stack overflow was reproduced at any depth tried
		// against networknt 3.0.5, so this bounds the work a hostile schema can demand
		// rather than fixing an observed crash.
		String schema = nestedObjectSchema(300);
		SchemaValidator validator = new SchemaValidator();
		// over the limit, the schema is unparseable -> treated as absent, so values that
		// would otherwise violate it are not rejected, and nothing crashes
		validator.validate("deep", schema, Map.of("a", "not-an-object"));
	}

	@Test
	void schemaWithinJhelmsLimitStillValidates() {
		// the limit must not break a legitimately nested schema: 40 levels (~80 JSON
		// levels, double the deepest real chart schemas) validates, and a violation at
		// the leaf is still caught
		String schema = nestedObjectSchema(40);
		SchemaValidator validator = new SchemaValidator();
		validator.validate("ok-depth", schema, nestedValues(40, "leaf-string"));
		assertThrows(SchemaValidationException.class,
				() -> validator.validate("ok-depth", schema, nestedValues(40, 42)));
	}

	private static String nestedObjectSchema(int depth) {
		StringBuilder schema = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			schema.append("{\"type\":\"object\",\"properties\":{\"a\":");
		}
		schema.append("{\"type\":\"string\"}");
		schema.append("}}".repeat(depth));
		return schema.toString();
	}

	private static Map<String, Object> nestedValues(int depth, Object leaf) {
		Map<String, Object> root = new HashMap<>();
		Map<String, Object> cursor = root;
		for (int i = 0; i < depth - 1; i++) {
			Map<String, Object> next = new HashMap<>();
			cursor.put("a", next);
			cursor = next;
		}
		cursor.put("a", leaf);
		return root;
	}

	@Test
	void oversizeSchemaFailsTheChartRatherThanSkippingValidation() {
		// Failing closed matters: silently skipping would let a hostile chart opt out of
		// its own constraints.
		String filler = "x".repeat(1024 * 1024);
		String schema = "{\"type\":\"object\",\"description\":\"" + filler + "\"}";
		SchemaValidator validator = new SchemaValidator();
		SchemaValidationException ex = assertThrows(SchemaValidationException.class,
				() -> validator.validate("huge", schema, Map.of()));
		assertTrue(ex.getMessage().contains("limit"), ex.getMessage());
	}

	@Test
	void remoteRefIsNeverFetched() throws Exception {
		// Stand up a local server and assert it is never hit. A chart must not be able to
		// make jhelm fetch a URL during validation — the remote-$ref memory-exhaustion
		// vector (GHSA-9h84-qmv7-982p), and an SSRF-shaped surface besides.
		AtomicInteger hits = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", (exchange) -> {
			hits.incrementAndGet();
			byte[] body = "{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		try {
			String ref = "http://127.0.0.1:" + server.getAddress().getPort() + "/evil.json";
			String schema = "{\"type\":\"object\",\"properties\":{\"a\":{\"$ref\":\"" + ref + "\"}}}";
			SchemaValidator validator = new SchemaValidator();
			try {
				validator.validate("remote-ref", schema, Map.of("a", "value"));
			}
			catch (SchemaValidationException ex) {
				assertNotNull(ex.getMessage());
			}
			assertEquals(0, hits.get(), "jhelm fetched the remote $ref");
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	void schemaCacheIsBounded() throws Exception {
		SchemaValidator validator = new SchemaValidator();
		for (int i = 0; i < 200; i++) {
			validator.validate("chart" + i, "{\"type\":\"object\",\"title\":\"s" + i + "\"}", Map.of());
		}
		Field field = SchemaValidator.class.getDeclaredField("schemaCache");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<String, ?> cache = (Map<String, ?>) field.get(validator);
		assertTrue(cache.size() <= 64, "cache grew to " + cache.size());
	}

	@Test
	void longRefChainDoesNotKillTheProcess() {
		// The GHSA-5xqw-8hwv-wg92 shape: a long CHAIN of $ref, in flat JSON that no
		// nesting limit can see. Measured against networknt 3.0.5, chains of 1k/50k/200k
		// links all return an ordinary validation error — it resolves refs without
		// per-ref recursion, so Helm's stack-overflow vector does not reproduce here.
		// Kept as a regression guard in case a library upgrade changes that.
		StringBuilder defs = new StringBuilder("{\"$ref\":\"#/$defs/r0\",\"$defs\":{");
		int links = 50_000;
		for (int i = 0; i < links; i++) {
			defs.append("\"r").append(i).append("\":{\"$ref\":\"#/$defs/r").append(i + 1).append("\"},");
		}
		defs.append("\"r").append(links).append("\":{\"type\":\"string\"}}}");
		SchemaValidator validator = new SchemaValidator();
		try {
			validator.validate("ref-chain", defs.toString(), Map.of("a", "value"));
		}
		catch (SchemaValidationException ex) {
			assertNotNull(ex.getMessage());
		}
	}

}
