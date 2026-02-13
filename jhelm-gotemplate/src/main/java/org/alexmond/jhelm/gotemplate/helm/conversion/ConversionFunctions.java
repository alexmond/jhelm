package org.alexmond.jhelm.gotemplate.helm.conversion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.alexmond.jhelm.gotemplate.Function;

import java.util.HashMap;
import java.util.Map;

/**
 * Helm conversion functions for YAML/JSON operations
 * Includes Helm 4 new functions: mustToYaml, mustToJson, mustFromJson, mustFromYaml
 * Based on: https://helm.sh/docs/chart_template_guide/function_list/
 */
public class ConversionFunctions {

    private static final ThreadLocal<ObjectMapper> YAML_MAPPER = ThreadLocal.withInitial(() -> {
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build();
        ObjectMapper mapper = new ObjectMapper(yamlFactory);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    });

    private static final ThreadLocal<ObjectMapper> JSON_MAPPER = ThreadLocal.withInitial(() -> {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    });

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
     * toYaml converts an object to YAML string
     * Returns empty string on error
     */
    private static Function toYaml() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";
            try {
                String yaml = YAML_MAPPER.get().writeValueAsString(args[0]);
                // Remove document start marker if present
                if (yaml.startsWith("---\n")) {
                    yaml = yaml.substring(4);
                }
                return yaml.trim();
            } catch (Exception e) {
                return "";
            }
        };
    }

    /**
     * mustToYaml converts an object to YAML string
     * Throws exception on error (Helm 4 new function)
     */
    private static Function mustToYaml() {
        return args -> {
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
            } catch (Exception e) {
                throw new RuntimeException("mustToYaml: failed to convert to YAML: " + e.getMessage(), e);
            }
        };
    }

    /**
     * fromYaml parses YAML string to object
     * Returns empty map on error
     */
    private static Function fromYaml() {
        return args -> {
            if (args.length == 0 || args[0] == null) return Map.of();
            try {
                String yaml = String.valueOf(args[0]);
                if (yaml.trim().isEmpty()) return Map.of();
                return YAML_MAPPER.get().readValue(yaml, Map.class);
            } catch (Exception e) {
                return Map.of();
            }
        };
    }

    /**
     * mustFromYaml parses YAML string to object
     * Throws exception on error (Helm 4 new function)
     */
    private static Function mustFromYaml() {
        return args -> {
            if (args.length == 0 || args[0] == null) {
                throw new RuntimeException("mustFromYaml: no YAML string provided");
            }
            try {
                String yaml = String.valueOf(args[0]);
                if (yaml.trim().isEmpty()) {
                    throw new RuntimeException("mustFromYaml: empty YAML string");
                }
                return YAML_MAPPER.get().readValue(yaml, Map.class);
            } catch (Exception e) {
                throw new RuntimeException("mustFromYaml: failed to parse YAML: " + e.getMessage(), e);
            }
        };
    }

    /**
     * fromYamlArray parses YAML string to array/list
     * Returns empty list on error
     */
    private static Function fromYamlArray() {
        return args -> {
            if (args.length == 0 || args[0] == null) return java.util.Collections.emptyList();
            try {
                String yaml = String.valueOf(args[0]);
                if (yaml.trim().isEmpty()) return java.util.Collections.emptyList();
                return YAML_MAPPER.get().readValue(yaml, java.util.List.class);
            } catch (Exception e) {
                return java.util.Collections.emptyList();
            }
        };
    }

    /**
     * mustFromYamlArray parses YAML string to array/list
     * Throws exception on error
     */
    private static Function mustFromYamlArray() {
        return args -> {
            if (args.length == 0 || args[0] == null) {
                throw new RuntimeException("mustFromYamlArray: no YAML string provided");
            }
            try {
                String yaml = String.valueOf(args[0]);
                if (yaml.trim().isEmpty()) {
                    throw new RuntimeException("mustFromYamlArray: empty YAML string");
                }
                return YAML_MAPPER.get().readValue(yaml, java.util.List.class);
            } catch (Exception e) {
                throw new RuntimeException("mustFromYamlArray: failed to parse YAML array: " + e.getMessage(), e);
            }
        };
    }

    // ===== JSON Functions =====

    /**
     * toJson converts an object to JSON string
     * Returns empty string on error
     */
    private static Function toJson() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "null";
            try {
                return JSON_MAPPER.get().writeValueAsString(args[0]);
            } catch (Exception e) {
                return "";
            }
        };
    }

    /**
     * mustToJson converts an object to JSON string
     * Throws exception on error (Helm 4 new function)
     */
    private static Function mustToJson() {
        return args -> {
            if (args.length == 0 || args[0] == null) {
                throw new RuntimeException("mustToJson: no value provided");
            }
            try {
                return JSON_MAPPER.get().writeValueAsString(args[0]);
            } catch (Exception e) {
                throw new RuntimeException("mustToJson: failed to convert to JSON: " + e.getMessage(), e);
            }
        };
    }

    /**
     * toPrettyJson converts an object to pretty-printed JSON string
     * Returns empty string on error
     */
    private static Function toPrettyJson() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "null";
            try {
                return JSON_MAPPER.get().writerWithDefaultPrettyPrinter().writeValueAsString(args[0]);
            } catch (Exception e) {
                return "";
            }
        };
    }

    /**
     * mustToPrettyJson converts an object to pretty-printed JSON string
     * Throws exception on error
     */
    private static Function mustToPrettyJson() {
        return args -> {
            if (args.length == 0 || args[0] == null) {
                throw new RuntimeException("mustToPrettyJson: no value provided");
            }
            try {
                return JSON_MAPPER.get().writerWithDefaultPrettyPrinter().writeValueAsString(args[0]);
            } catch (Exception e) {
                throw new RuntimeException("mustToPrettyJson: failed to convert to JSON: " + e.getMessage(), e);
            }
        };
    }

    /**
     * toRawJson converts an object to JSON string without HTML escaping
     * Returns empty string on error
     */
    private static Function toRawJson() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "null";
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.getFactory().disable(com.fasterxml.jackson.core.JsonGenerator.Feature.ESCAPE_NON_ASCII);
                return mapper.writeValueAsString(args[0]);
            } catch (Exception e) {
                return "";
            }
        };
    }

    /**
     * mustToRawJson converts an object to JSON string without HTML escaping
     * Throws exception on error
     */
    private static Function mustToRawJson() {
        return args -> {
            if (args.length == 0 || args[0] == null) {
                throw new RuntimeException("mustToRawJson: no value provided");
            }
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.getFactory().disable(com.fasterxml.jackson.core.JsonGenerator.Feature.ESCAPE_NON_ASCII);
                return mapper.writeValueAsString(args[0]);
            } catch (Exception e) {
                throw new RuntimeException("mustToRawJson: failed to convert to JSON: " + e.getMessage(), e);
            }
        };
    }

    /**
     * fromJson parses JSON string to object
     * Returns empty map on error
     */
    private static Function fromJson() {
        return args -> {
            if (args.length == 0 || args[0] == null) return Map.of();
            try {
                String json = String.valueOf(args[0]);
                if (json.trim().isEmpty() || json.equals("null")) return Map.of();
                return JSON_MAPPER.get().readValue(json, Map.class);
            } catch (Exception e) {
                return Map.of();
            }
        };
    }

    /**
     * mustFromJson parses JSON string to object
     * Throws exception on error (Helm 4 new function)
     */
    private static Function mustFromJson() {
        return args -> {
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
            } catch (Exception e) {
                throw new RuntimeException("mustFromJson: failed to parse JSON: " + e.getMessage(), e);
            }
        };
    }

    /**
     * fromJsonArray parses JSON string to array/list
     * Returns empty list on error
     */
    private static Function fromJsonArray() {
        return args -> {
            if (args.length == 0 || args[0] == null) return java.util.Collections.emptyList();
            try {
                String json = String.valueOf(args[0]);
                if (json.trim().isEmpty() || json.equals("null")) return java.util.Collections.emptyList();
                return JSON_MAPPER.get().readValue(json, java.util.List.class);
            } catch (Exception e) {
                return java.util.Collections.emptyList();
            }
        };
    }

    /**
     * mustFromJsonArray parses JSON string to array/list
     * Throws exception on error
     */
    private static Function mustFromJsonArray() {
        return args -> {
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
                return JSON_MAPPER.get().readValue(json, java.util.List.class);
            } catch (Exception e) {
                throw new RuntimeException("mustFromJsonArray: failed to parse JSON array: " + e.getMessage(), e);
            }
        };
    }
}
