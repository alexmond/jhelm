package org.alexmond.jhelm.core.service;

import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.exception.SchemaValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

}
