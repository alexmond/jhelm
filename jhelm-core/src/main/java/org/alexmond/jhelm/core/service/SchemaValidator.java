package org.alexmond.jhelm.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.SchemaValidationException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates Helm chart values against a JSON Schema declared in
 * {@code values.schema.json}. Supports the most common JSON Schema Draft-07 constraints:
 * {@code type}, {@code required}, {@code properties}, {@code enum}, {@code minimum},
 * {@code maximum}, {@code minLength}, {@code maxLength}, and {@code pattern}.
 * <p>
 * A malformed schema is logged as a warning and treated as absent — consistent with real
 * Helm behaviour.
 * </p>
 */
@Slf4j
public class SchemaValidator {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	/**
	 * Validates the given values map against the JSON Schema.
	 * @param chartName chart name used in error messages
	 * @param schemaJson raw JSON content of {@code values.schema.json}, or {@code null}
	 * @param values merged values to validate
	 * @throws org.alexmond.jhelm.core.exception.SchemaValidationException if any
	 * constraint is violated
	 */
	public void validate(String chartName, String schemaJson, Map<String, Object> values) {
		if (schemaJson == null || schemaJson.isBlank()) {
			return;
		}
		List<String> errors = new ArrayList<>();
		try {
			JsonNode schema = JSON_MAPPER.readTree(schemaJson);
			JsonNode valuesNode = JSON_MAPPER.valueToTree(values);
			validateNode(schema, valuesNode, "$", errors);
		}
		catch (Exception ex) {
			log.warn("Could not parse values.schema.json for chart {}: {}", chartName, ex.getMessage());
			return;
		}
		if (!errors.isEmpty()) {
			throw new SchemaValidationException(chartName, errors);
		}
	}

	private void validateNode(JsonNode schema, JsonNode value, String path, List<String> errors) {
		if (schema == null || schema.isMissingNode()) {
			return;
		}
		if (schema.has("type")) {
			validateType(schema.get("type").asText(), value, path, errors);
		}
		if (schema.has("required") && value.isObject()) {
			for (JsonNode req : schema.get("required")) {
				if (!value.has(req.asText())) {
					errors.add(path + "." + req.asText() + " is required");
				}
			}
		}
		if (schema.has("properties") && value.isObject()) {
			JsonNode props = schema.get("properties");
			Map<String, JsonNode> propsMap = JSON_MAPPER.convertValue(props,
					new TypeReference<Map<String, JsonNode>>() {
					});
			for (Map.Entry<String, JsonNode> entry : propsMap.entrySet()) {
				if (value.has(entry.getKey())) {
					validateNode(entry.getValue(), value.get(entry.getKey()), path + "." + entry.getKey(), errors);
				}
			}
		}
		if (schema.has("enum")) {
			boolean valid = false;
			for (JsonNode enumVal : schema.get("enum")) {
				if (enumVal.equals(value)) {
					valid = true;
					break;
				}
			}
			if (!valid) {
				errors.add(path + ": value " + value + " is not one of the allowed values " + schema.get("enum"));
			}
		}
		if (value.isNumber()) {
			double num = value.doubleValue();
			if (schema.has("minimum") && num < schema.get("minimum").doubleValue()) {
				errors.add(path + ": " + num + " must be >= " + schema.get("minimum").doubleValue());
			}
			if (schema.has("maximum") && num > schema.get("maximum").doubleValue()) {
				errors.add(path + ": " + num + " must be <= " + schema.get("maximum").doubleValue());
			}
		}
		if (value.isTextual()) {
			String text = value.asText();
			if (schema.has("minLength") && text.length() < schema.get("minLength").asInt()) {
				errors
					.add(path + ": string length " + text.length() + " must be >= " + schema.get("minLength").asInt());
			}
			if (schema.has("maxLength") && text.length() > schema.get("maxLength").asInt()) {
				errors
					.add(path + ": string length " + text.length() + " must be <= " + schema.get("maxLength").asInt());
			}
			if (schema.has("pattern")) {
				String patternStr = schema.get("pattern").asText();
				try {
					if (!Pattern.compile(patternStr).matcher(text).find()) {
						errors.add(path + ": string does not match pattern " + patternStr);
					}
				}
				catch (PatternSyntaxException ex) {
					log.warn("Invalid pattern '{}' in schema at {}: {}", patternStr, path, ex.getMessage());
				}
			}
		}
	}

	private void validateType(String type, JsonNode value, String path, List<String> errors) {
		boolean valid = switch (type) {
			case "string" -> value.isTextual();
			case "integer" -> value.isIntegralNumber();
			case "number" -> value.isNumber();
			case "boolean" -> value.isBoolean();
			case "array" -> value.isArray();
			case "object" -> value.isObject();
			case "null" -> value.isNull();
			default -> true;
		};
		if (!valid) {
			errors.add(path + ": expected type " + type + " but was " + nodeTypeName(value));
		}
	}

	private String nodeTypeName(JsonNode node) {
		if (node.isTextual()) {
			return "string";
		}
		if (node.isIntegralNumber()) {
			return "integer";
		}
		if (node.isFloatingPointNumber()) {
			return "number";
		}
		if (node.isBoolean()) {
			return "boolean";
		}
		if (node.isArray()) {
			return "array";
		}
		if (node.isObject()) {
			return "object";
		}
		if (node.isNull()) {
			return "null";
		}
		return "unknown";
	}

}
