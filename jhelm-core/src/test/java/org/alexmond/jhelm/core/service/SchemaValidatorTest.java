package org.alexmond.jhelm.core.service;

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

}
