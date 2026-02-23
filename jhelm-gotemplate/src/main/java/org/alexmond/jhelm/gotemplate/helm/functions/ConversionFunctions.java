package org.alexmond.jhelm.gotemplate.helm.functions;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * Helm conversion functions for YAML/JSON operations Includes Helm 4 new functions:
 * mustToYaml, mustToJson, mustFromJson, mustFromYaml Based on: <a href=
 * "https://helm.sh/docs/chart_template_guide/function_list/">https://helm.sh/docs/chart_template_guide/function_list/</a>
 */
public final class ConversionFunctions {

	private ConversionFunctions() {
	}

	private static final ThreadLocal<YAMLMapper> YAML_MAPPER = ThreadLocal.withInitial(() -> YAMLMapper.builder()
		.disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
		.enable(YAMLWriteFeature.MINIMIZE_QUOTES)
		// Sort keys alphabetically for consistent, predictable output
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
		.build());

	private static final ThreadLocal<JsonMapper> JSON_MAPPER = ThreadLocal
		.withInitial(() -> JsonMapper.builder().build());

	private static final ThreadLocal<JsonMapper> RAW_JSON_MAPPER = ThreadLocal
		.withInitial(() -> JsonMapper.builder().disable(JsonWriteFeature.ESCAPE_NON_ASCII).build());

	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();

		// YAML functions
		functions.put("toYaml", toYaml());
		functions.put("mustToYaml", mustToYaml());
		functions.put("fromYaml", fromYaml());
		functions.put("mustFromYaml", mustFromYaml());
		functions.put("fromYamlArray", fromYamlArray());
		functions.put("mustFromYamlArray", mustFromYamlArray());

		// JSON functions
		functions.put("toJson", toJson());
		functions.put("mustToJson", mustToJson());
		functions.put("toPrettyJson", toPrettyJson());
		functions.put("mustToPrettyJson", mustToPrettyJson());
		functions.put("toRawJson", toRawJson());
		functions.put("mustToRawJson", mustToRawJson());
		functions.put("fromJson", fromJson());
		functions.put("mustFromJson", mustFromJson());
		functions.put("fromJsonArray", fromJsonArray());
		functions.put("mustFromJsonArray", mustFromJsonArray());

		return functions;
	}

	// ===== YAML Functions =====

	/**
	 * toYaml converts an object to YAML string Returns empty string on error
	 */
	private static Function toYaml() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "";
			}
			try {
				String yaml = YAML_MAPPER.get().writeValueAsString(args[0]);
				// Remove document start marker if present
				if (yaml.startsWith("---\n")) {
					yaml = yaml.substring(4);
				}
				return yaml.trim();
			}
			catch (Exception ex) {
				return "";
			}
		};
	}

	/**
	 * mustToYaml converts an object to YAML string Throws exception on error (Helm 4 new
	 * function)
	 */
	private static Function mustToYaml() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				throw new RuntimeException("mustToYaml: no value provided");
			}
			try {
				String yaml = YAML_MAPPER.get().writeValueAsString(args[0]);
				// Remove document start marker if present
				if (yaml.startsWith("---\n")) {
					yaml = yaml.substring(4);
				}
				return yaml.trim();
			}
			catch (Exception ex) {
				throw new RuntimeException("mustToYaml: failed to convert to YAML: " + ex.getMessage(), ex);
			}
		};
	}

	/**
	 * fromYaml parses YAML string to object Returns empty map on error
	 */
	private static Function fromYaml() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return Map.of();
			}
			try {
				String yaml = String.valueOf(args[0]);
				if (yaml.trim().isEmpty()) {
					return Map.of();
				}
				return YAML_MAPPER.get().readValue(yaml, Map.class);
			}
			catch (Exception ex) {
				return Map.of();
			}
		};
	}

	/**
	 * mustFromYaml parses YAML string to object Throws exception on error (Helm 4 new
	 * function)
	 */
	private static Function mustFromYaml() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				throw new RuntimeException("mustFromYaml: no YAML string provided");
			}
			try {
				String yaml = String.valueOf(args[0]);
				if (yaml.trim().isEmpty()) {
					throw new RuntimeException("mustFromYaml: empty YAML string");
				}
				return YAML_MAPPER.get().readValue(yaml, Map.class);
			}
			catch (Exception ex) {
				throw new RuntimeException("mustFromYaml: failed to parse YAML: " + ex.getMessage(), ex);
			}
		};
	}

	/**
	 * fromYamlArray parses YAML string to array/list Returns empty list on error
	 */
	private static Function fromYamlArray() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return Collections.emptyList();
			}
			try {
				String yaml = String.valueOf(args[0]);
				if (yaml.trim().isEmpty()) {
					return Collections.emptyList();
				}
				return YAML_MAPPER.get().readValue(yaml, List.class);
			}
			catch (Exception ex) {
				return Collections.emptyList();
			}
		};
	}

	/**
	 * mustFromYamlArray parses YAML string to array/list Throws exception on error
	 */
	private static Function mustFromYamlArray() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				throw new RuntimeException("mustFromYamlArray: no YAML string provided");
			}
			try {
				String yaml = String.valueOf(args[0]);
				if (yaml.trim().isEmpty()) {
					throw new RuntimeException("mustFromYamlArray: empty YAML string");
				}
				return YAML_MAPPER.get().readValue(yaml, List.class);
			}
			catch (Exception ex) {
				throw new RuntimeException("mustFromYamlArray: failed to parse YAML array: " + ex.getMessage(), ex);
			}
		};
	}

	// ===== JSON Functions =====

	/**
	 * toJson converts an object to JSON string Returns empty string on error
	 */
	private static Function toJson() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "null";
			}
			try {
				return JSON_MAPPER.get().writeValueAsString(args[0]);
			}
			catch (Exception ex) {
				return "";
			}
		};
	}

	/**
	 * mustToJson converts an object to JSON string Throws exception on error (Helm 4 new
	 * function)
	 */
	private static Function mustToJson() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				throw new RuntimeException("mustToJson: no value provided");
			}
			try {
				return JSON_MAPPER.get().writeValueAsString(args[0]);
			}
			catch (Exception ex) {
				throw new RuntimeException("mustToJson: failed to convert to JSON: " + ex.getMessage(), ex);
			}
		};
	}

	/**
	 * toPrettyJson converts an object to pretty-printed JSON string Returns empty string
	 * on error
	 */
	private static Function toPrettyJson() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "null";
			}
			try {
				return JSON_MAPPER.get().writerWithDefaultPrettyPrinter().writeValueAsString(args[0]);
			}
			catch (Exception ex) {
				return "";
			}
		};
	}

	/**
	 * mustToPrettyJson converts an object to pretty-printed JSON string Throws exception
	 * on error
	 */
	private static Function mustToPrettyJson() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				throw new RuntimeException("mustToPrettyJson: no value provided");
			}
			try {
				return JSON_MAPPER.get().writerWithDefaultPrettyPrinter().writeValueAsString(args[0]);
			}
			catch (Exception ex) {
				throw new RuntimeException("mustToPrettyJson: failed to convert to JSON: " + ex.getMessage(), ex);
			}
		};
	}

	/**
	 * toRawJson converts an object to JSON string without HTML escaping Returns empty
	 * string on error
	 */
	private static Function toRawJson() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "null";
			}
			try {
				return RAW_JSON_MAPPER.get().writeValueAsString(args[0]);
			}
			catch (Exception ex) {
				return "";
			}
		};
	}

	/**
	 * mustToRawJson converts an object to JSON string without HTML escaping Throws
	 * exception on error
	 */
	private static Function mustToRawJson() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				throw new RuntimeException("mustToRawJson: no value provided");
			}
			try {
				return RAW_JSON_MAPPER.get().writeValueAsString(args[0]);
			}
			catch (Exception ex) {
				throw new RuntimeException("mustToRawJson: failed to convert to JSON: " + ex.getMessage(), ex);
			}
		};
	}

	/**
	 * fromJson parses JSON string to object Returns empty map on error
	 */
	private static Function fromJson() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return Map.of();
			}
			try {
				String json = String.valueOf(args[0]);
				if (json.trim().isEmpty() || json.equals("null")) {
					return Map.of();
				}
				return JSON_MAPPER.get().readValue(json, Map.class);
			}
			catch (Exception ex) {
				return Map.of();
			}
		};
	}

	/**
	 * mustFromJson parses JSON string to object Throws exception on error (Helm 4 new
	 * function)
	 */
	private static Function mustFromJson() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				throw new RuntimeException("mustFromJson: no JSON string provided");
			}
			try {
				String json = String.valueOf(args[0]);
				if (json.trim().isEmpty()) {
					throw new RuntimeException("mustFromJson: empty JSON string");
				}
				if (json.equals("null")) {
					throw new RuntimeException("mustFromJson: cannot parse null");
				}
				return JSON_MAPPER.get().readValue(json, Map.class);
			}
			catch (Exception ex) {
				throw new RuntimeException("mustFromJson: failed to parse JSON: " + ex.getMessage(), ex);
			}
		};
	}

	/**
	 * fromJsonArray parses JSON string to array/list Returns empty list on error
	 */
	private static Function fromJsonArray() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return Collections.emptyList();
			}
			try {
				String json = String.valueOf(args[0]);
				if (json.trim().isEmpty() || json.equals("null")) {
					return Collections.emptyList();
				}
				return JSON_MAPPER.get().readValue(json, List.class);
			}
			catch (Exception ex) {
				return Collections.emptyList();
			}
		};
	}

	/**
	 * mustFromJsonArray parses JSON string to array/list Throws exception on error
	 */
	private static Function mustFromJsonArray() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				throw new RuntimeException("mustFromJsonArray: no JSON string provided");
			}
			try {
				String json = String.valueOf(args[0]);
				if (json.trim().isEmpty()) {
					throw new RuntimeException("mustFromJsonArray: empty JSON string");
				}
				if (json.equals("null")) {
					throw new RuntimeException("mustFromJsonArray: cannot parse null");
				}
				return JSON_MAPPER.get().readValue(json, List.class);
			}
			catch (Exception ex) {
				throw new RuntimeException("mustFromJsonArray: failed to parse JSON array: " + ex.getMessage(), ex);
			}
		};
	}

}
