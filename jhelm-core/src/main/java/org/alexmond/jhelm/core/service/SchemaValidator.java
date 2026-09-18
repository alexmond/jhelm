package org.alexmond.jhelm.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.networknt.schema.InputFormat;
import com.networknt.schema.OutputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.output.OutputUnit;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.SchemaValidationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates Helm chart values against the JSON Schema declared in a chart's
 * {@code values.schema.json}, using the full-spec
 * <a href="https://github.com/networknt/json-schema-validator">NetworkNT</a> validator.
 * <p>
 * The entire JSON Schema vocabulary is supported — {@code $ref}/{@code $defs},
 * {@code additionalProperties}, {@code items}, {@code oneOf}/{@code anyOf}/{@code allOf},
 * {@code if}/{@code then}/{@code else}, {@code const}, {@code patternProperties}, and the
 * rest — matching real Helm (Helm&nbsp;4 validates with santhosh-tekuri/jsonschema at
 * draft 2020-12; Helm&nbsp;3 with gojsonschema at Draft-07).
 * </p>
 * <p>
 * A schema with no {@code $schema} keyword is interpreted as draft 2020-12 (Helm&nbsp;4's
 * default); a schema that declares its own {@code $schema} (for example the Draft-07 URI
 * shipped by most existing charts) is validated against that draft. A malformed schema is
 * logged as a warning and treated as absent — consistent with real Helm behaviour.
 * </p>
 * <p>
 * Compiled schemas are cached by their raw content, so a chart rendered repeatedly parses
 * its schema once. The class is thread-safe.
 * </p>
 */
@Slf4j
public class SchemaValidator {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	/**
	 * Compiles schemas with draft 2020-12 as the default dialect (Helm 4's default) while
	 * still honouring a schema's own {@code $schema} when present.
	 */
	private final SchemaRegistry schemaRegistry;

	private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();

	public SchemaValidator() {
		SchemaRegistryConfig config = SchemaRegistryConfig.builder().build();
		this.schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
				(builder) -> builder.schemaRegistryConfig(config));
	}

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
		Schema schema = compile(chartName, schemaJson);
		if (schema == null) {
			// Malformed schema — already logged; treat as absent, like Helm.
			return;
		}
		String valuesJson;
		try {
			valuesJson = JSON_MAPPER.writeValueAsString((values != null) ? values : Map.of());
		}
		catch (RuntimeException ex) {
			if (log.isWarnEnabled()) {
				log.warn("Could not serialize values for schema validation of chart {}: {}", chartName,
						ex.getMessage());
			}
			return;
		}
		OutputUnit result = schema.validate(valuesJson, InputFormat.JSON, OutputFormat.LIST);
		if (result.isValid()) {
			return;
		}
		List<String> errors = collectErrors(result);
		if (!errors.isEmpty()) {
			throw new SchemaValidationException(chartName, errors);
		}
	}

	private Schema compile(String chartName, String schemaJson) {
		Schema cached = this.schemaCache.get(schemaJson);
		if (cached != null) {
			return cached;
		}
		try {
			JsonNode schemaNode = JSON_MAPPER.readTree(schemaJson);
			Schema schema = this.schemaRegistry.getSchema(SchemaLocation.of("values.schema.json"), schemaNode);
			schema.initializeValidators();
			this.schemaCache.put(schemaJson, schema);
			return schema;
		}
		catch (RuntimeException ex) {
			if (log.isWarnEnabled()) {
				log.warn("Could not parse values.schema.json for chart {}: {}", chartName, ex.getMessage());
			}
			return null;
		}
	}

	/**
	 * Flattens a NetworkNT {@link OutputUnit} into human-readable error strings, one per
	 * failing keyword, each prefixed with the JSON-pointer location of the offending
	 * value.
	 * @param result the validation output
	 * @return the collected error messages
	 */
	private List<String> collectErrors(OutputUnit result) {
		List<String> errors = new ArrayList<>();
		// Root-level errors (keyword -> message), e.g. a top-level required/type failure.
		if (result.getErrors() != null) {
			result.getErrors().forEach((keyword, message) -> errors.add(format("", keyword, message)));
		}
		// Per-instance violations discovered deeper in the document.
		if (result.getDetails() != null) {
			for (OutputUnit detail : result.getDetails()) {
				if (detail.getErrors() == null) {
					continue;
				}
				String location = detail.getInstanceLocation();
				detail.getErrors().forEach((keyword, message) -> errors.add(format(location, keyword, message)));
			}
		}
		return errors;
	}

	private String format(String location, Object keyword, Object message) {
		String pointer = (location == null || location.isEmpty()) ? "" : location + ": ";
		return pointer + message + " [" + keyword + "]";
	}

}
