package org.alexmond.jhelm.gotemplate.helm.functions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.alexmond.jhelm.gotemplate.Function;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.constructor.core.ConstructYamlCoreInt;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.CoreScalarResolver;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;
import org.snakeyaml.engine.v2.schema.CoreSchema;
import org.snakeyaml.engine.v2.schema.Schema;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.dataformat.toml.TomlMapper;
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
		// Go's yaml.Marshal never wraps long lines; Jackson defaults to 80-char width
		// which inserts \ line continuations that break tpl(toYaml ...) patterns (#203)
		.disable(YAMLWriteFeature.SPLIT_LINES)
		.enable(YAMLWriteFeature.MINIMIZE_QUOTES)
		// Keep quotes on numeric-looking strings to match Go yaml.Marshal behavior
		.enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
		// Sort keys alphabetically for consistent, predictable output
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
		.build());

	/**
	 * YAML 1.1 octal pattern: bare-zero prefix followed by octal digits (e.g. 0660,
	 * 0755). Go's yaml.v3 retains YAML 1.1 compatibility for these literals, parsing them
	 * as integers. SnakeYAML Engine (YAML 1.2 strict) only recognises 0o prefix.
	 */
	private static final Pattern YAML11_OCTAL = Pattern.compile("^0[0-7]+$");

	/**
	 * SnakeYAML Engine LoadSettings for fromYaml/fromYamlArray. Uses a custom schema with
	 * YAML 1.1 octal support and tilde-null resolution. SnakeYAML Engine is used directly
	 * (instead of Jackson YAMLMapper) so that the custom schema constructors are honoured
	 * — Jackson's YAMLMapper bypasses SnakeYAML's ConstructNode pipeline.
	 */
	private static final LoadSettings YAML_READ_SETTINGS = createYamlReadSettings();

	private static LoadSettings createYamlReadSettings() {
		CoreScalarResolver resolver = new CoreScalarResolver(true);
		resolver.addImplicitResolver(Tag.NULL, CoreScalarResolver.NULL, "~");
		resolver.addImplicitResolver(Tag.INT, YAML11_OCTAL, "0");

		Map<Tag, ConstructNode> constructors = new HashMap<>(new CoreSchema().getSchemaTagConstructors());
		constructors.put(Tag.INT, new Yaml11OctalIntConstructor());

		Schema schema = new Schema() {
			@Override
			public ScalarResolver getScalarResolver() {
				return resolver;
			}

			@Override
			public Map<Tag, ConstructNode> getSchemaTagConstructors() {
				return constructors;
			}
		};

		return LoadSettings.builder().setSchema(schema).build();
	}

	/** Pattern matching a YAML line with a double-quoted scalar value. */
	private static final Pattern QUOTED_VALUE = Pattern.compile("^(\\s*\\S+:\\s+)\"((?:[^\"\\\\]|\\\\.)*)\"\\s*$");

	/** YAML boolean and null literals that must remain quoted. */
	private static final Set<String> YAML_KEYWORDS = Set.of("true", "false", "yes", "no", "on", "off", "null", "~");

	/** Pattern matching numeric values (integer, float, hex, octal, infinity, NaN). */
	private static final Pattern NUMERIC = Pattern
		.compile("^[+-]?(\\d[\\d_]*(\\.[\\d_]*)?([eE][+-]?\\d+)?|\\.inf|\\.nan|0x[\\da-fA-F]+|0o[0-7]+)$");

	private static final ThreadLocal<JsonMapper> JSON_MAPPER = ThreadLocal.withInitial(() -> JsonMapper.builder()
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
		.addModule(goNumberModule())
		.build());

	private static final ThreadLocal<JsonMapper> RAW_JSON_MAPPER = ThreadLocal.withInitial(() -> JsonMapper.builder()
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
		.disable(JsonWriteFeature.ESCAPE_NON_ASCII)
		.addModule(goNumberModule())
		.build());

	private static final ThreadLocal<TomlMapper> TOML_MAPPER = ThreadLocal
		.withInitial(() -> TomlMapper.builder().build());

	/**
	 * Go's json.Marshal normalizes whole-number float64 values to integer representation
	 * (1.0 → 1). This module replicates that behavior.
	 */
	private static SimpleModule goNumberModule() {
		SimpleModule module = new SimpleModule("GoNumberModule");
		module.addSerializer(Double.class, new StdSerializer<>(Double.class) {
			@Override
			public void serialize(Double value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
				if (value == Math.floor(value) && !Double.isInfinite(value) && !Double.isNaN(value)
						&& value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
					gen.writeNumber(value.longValue());
				}
				else {
					gen.writeNumber(value);
				}
			}
		});
		return module;
	}

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

		// TOML functions
		functions.put("toToml", toToml());
		functions.put("mustToToml", mustToToml());
		functions.put("fromToml", fromToml());
		functions.put("mustFromToml", mustFromToml());

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
				return removeUnnecessaryQuotes(yaml.trim());
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
				return removeUnnecessaryQuotes(yaml.trim());
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
				if (yaml.isBlank()) {
					return Map.of();
				}
				Object result = loadFirstYamlDocument(yaml);
				return (result instanceof Map<?, ?> map) ? map : Map.of();
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
				if (yaml.isBlank()) {
					throw new RuntimeException("mustFromYaml: empty YAML string");
				}
				Object result = loadFirstYamlDocument(yaml);
				if (result instanceof Map<?, ?>) {
					return result;
				}
				throw new RuntimeException("mustFromYaml: expected map but got " + result);
			}
			catch (RuntimeException ex) {
				throw ex;
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
				if (yaml.isBlank()) {
					return Collections.emptyList();
				}
				Object result = loadFirstYamlDocument(yaml);
				return (result instanceof List<?> list) ? list : Collections.emptyList();
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
				if (yaml.isBlank()) {
					throw new RuntimeException("mustFromYamlArray: empty YAML string");
				}
				Object result = loadFirstYamlDocument(yaml);
				if (result instanceof List<?>) {
					return result;
				}
				throw new RuntimeException("mustFromYamlArray: expected list but got " + result);
			}
			catch (RuntimeException ex) {
				throw ex;
			}
			catch (Exception ex) {
				throw new RuntimeException("mustFromYamlArray: failed to parse YAML array: " + ex.getMessage(), ex);
			}
		};
	}

	/**
	 * Loads the first YAML document from a string using SnakeYAML Engine directly. Go's
	 * yaml.Unmarshal reads only the first document and ignores trailing content, so we
	 * use {@code loadAllFromString} and take the first result.
	 * <p>
	 * Input is normalised by stripping trailing whitespace to handle block scalar
	 * indicators ({@code |-}) at EOF without content.
	 */
	private static Object loadFirstYamlDocument(String yaml) {
		Load load = new Load(YAML_READ_SETTINGS);
		Iterator<Object> docs = load.loadAllFromString(yaml.stripTrailing()).iterator();
		return docs.hasNext() ? docs.next() : null;
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
				if (json.isBlank() || "null".equals(json)) {
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
				if (json.isBlank()) {
					throw new RuntimeException("mustFromJson: empty JSON string");
				}
				if ("null".equals(json)) {
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
				if (json.isBlank() || "null".equals(json)) {
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
				if (json.isBlank()) {
					throw new RuntimeException("mustFromJsonArray: empty JSON string");
				}
				if ("null".equals(json)) {
					throw new RuntimeException("mustFromJsonArray: cannot parse null");
				}
				return JSON_MAPPER.get().readValue(json, List.class);
			}
			catch (Exception ex) {
				throw new RuntimeException("mustFromJsonArray: failed to parse JSON array: " + ex.getMessage(), ex);
			}
		};
	}

	// ===== TOML Functions =====

	private static Function toToml() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "";
			}
			try {
				return TOML_MAPPER.get().writeValueAsString(args[0]);
			}
			catch (Exception ex) {
				return "";
			}
		};
	}

	private static Function mustToToml() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				throw new RuntimeException("mustToToml: no value provided");
			}
			try {
				return TOML_MAPPER.get().writeValueAsString(args[0]);
			}
			catch (Exception ex) {
				throw new RuntimeException("mustToToml: failed to convert to TOML: " + ex.getMessage(), ex);
			}
		};
	}

	private static Function fromToml() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return Map.of();
			}
			try {
				String toml = String.valueOf(args[0]);
				if (toml.isBlank()) {
					return Map.of();
				}
				return TOML_MAPPER.get().readValue(toml, Map.class);
			}
			catch (Exception ex) {
				return Map.of();
			}
		};
	}

	private static Function mustFromToml() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				throw new RuntimeException("mustFromToml: no TOML string provided");
			}
			try {
				String toml = String.valueOf(args[0]);
				if (toml.isBlank()) {
					throw new RuntimeException("mustFromToml: empty TOML string");
				}
				return TOML_MAPPER.get().readValue(toml, Map.class);
			}
			catch (Exception ex) {
				throw new RuntimeException("mustFromToml: failed to parse TOML: " + ex.getMessage(), ex);
			}
		};
	}

	// ===== YAML Helpers =====

	/**
	 * Removes unnecessary double-quoting from YAML scalar values.
	 * Jackson/snakeyaml-engine over-quotes strings containing flow indicators
	 * ({@code {}[]}) even when they appear mid-string and are valid in YAML plain style.
	 * Go's yaml.Marshal uses plain style in these cases.
	 */
	static String removeUnnecessaryQuotes(String yaml) {
		StringBuilder sb = new StringBuilder();
		for (String line : yaml.split("\n", -1)) {
			Matcher m = QUOTED_VALUE.matcher(line);
			if (m.matches()) {
				String prefix = m.group(1);
				String escaped = m.group(2);
				String unescaped = yamlUnescape(escaped);
				if (canBePlainScalar(unescaped)) {
					sb.append(prefix).append(unescaped);
				}
				else {
					sb.append(line);
				}
			}
			else {
				sb.append(line);
			}
			sb.append('\n');
		}
		// Remove trailing newline added by loop
		if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
			sb.setLength(sb.length() - 1);
		}
		return sb.toString();
	}

	private static String yamlUnescape(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '\\' && i + 1 < s.length()) {
				char next = s.charAt(i + 1);
				switch (next) {
					case '\\' -> sb.append('\\');
					case '"' -> sb.append('"');
					case 'n' -> sb.append('\n');
					case 't' -> sb.append('\t');
					case 'r' -> sb.append('\r');
					default -> {
						sb.append('\\');
						sb.append(next);
					}
				}
				i++;
			}
			else {
				sb.append(s.charAt(i));
			}
		}
		return sb.toString();
	}

	private static boolean canBePlainScalar(String s) {
		if (s.isEmpty() || s.contains("\n")) {
			return false;
		}
		// Must not start or end with whitespace
		if (Character.isWhitespace(s.charAt(0)) || Character.isWhitespace(s.charAt(s.length() - 1))) {
			return false;
		}
		char first = s.charAt(0);
		// Must not start with YAML indicators
		if (first == '[' || first == '{' || first == '!' || first == '&' || first == '*' || first == '|' || first == '>'
				|| first == '%' || first == '@' || first == '`' || first == '\'' || first == '"') {
			return false;
		}
		// Must not start with - or ? followed by space
		if ((first == '-' || first == '?') && s.length() > 1 && s.charAt(1) == ' ') {
			return false;
		}
		// Must not contain ": " (mapping indicator) or " #" (comment indicator)
		if (s.contains(": ") || s.contains(" #")) {
			return false;
		}
		// Must not be a YAML boolean/null keyword
		if (YAML_KEYWORDS.contains(s.toLowerCase(Locale.ROOT))) {
			return false;
		}
		// Must not look like a number (would lose string type)
		if (NUMERIC.matcher(s).matches()) {
			return false;
		}
		return true;
	}

	// ===== YAML 1.1 Octal Support =====

	/**
	 * Integer constructor that recognises YAML 1.1 bare-octal literals ({@code 0660}) in
	 * addition to the YAML 1.2 formats handled by {@link ConstructYamlCoreInt}.
	 */
	static final class Yaml11OctalIntConstructor extends ConstructYamlCoreInt {

		@Override
		public Object construct(Node node) {
			String value = constructScalar(node);
			if (value.length() > 1 && value.charAt(0) == '0' && YAML11_OCTAL.matcher(value).matches()) {
				return createLongOrBigInteger(value.substring(1), 8);
			}
			return super.construct(node);
		}

	}

}
