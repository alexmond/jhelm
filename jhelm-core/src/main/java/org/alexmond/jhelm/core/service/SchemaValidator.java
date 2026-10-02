package org.alexmond.jhelm.core.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.networknt.schema.OutputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.output.OutputUnit;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.SchemaValidationException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.json.JsonFactory;
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

	/**
	 * Largest {@code values.schema.json} jhelm will parse. A chart comes from a
	 * repository, so the schema is untrusted input; Helm has fixed a schema that exhausts
	 * memory (GHSA-9h84-qmv7-982p) and one that overflows the stack
	 * (GHSA-5xqw-8hwv-wg92). Neither crash reproduced against networknt 3.0.5, so this
	 * bounds the work a hostile schema can demand rather than fixing an observed crash.
	 * Real chart schemas are a few KB, so 1 MiB leaves ample room (#831).
	 */
	private static final int MAX_SCHEMA_BYTES = 1024 * 1024;

	/**
	 * Nesting limit for both the schema and the values document, counted in JSON levels.
	 * Deep nesting is what turns into parser/validator recursion and a stack overflow.
	 * Note a schema spends roughly two JSON levels per level of described structure
	 * ({@code properties} then the property), so 200 allows about 100 levels of nested
	 * values — real chart schemas are well under 20, and Jackson's own default (1000) is
	 * too loose to prevent deep recursion.
	 */
	private static final int MAX_NESTING_DEPTH = 200;

	/**
	 * Compiled schemas retained, bounding the cache a chart loop could otherwise grow.
	 */
	private static final int MAX_CACHED_SCHEMAS = 64;

	private static final JsonMapper JSON_MAPPER = JsonMapper
		.builder(JsonFactory.builder()
			.streamReadConstraints(StreamReadConstraints.builder()
				.maxNestingDepth(MAX_NESTING_DEPTH)
				.maxDocumentLength(MAX_SCHEMA_BYTES)
				.build())
			.streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(MAX_NESTING_DEPTH).build())
			.build())
		.build();

	/**
	 * Compiles schemas with draft 2020-12 as the default dialect (Helm 4's default) while
	 * still honouring a schema's own {@code $schema} when present.
	 */
	private final SchemaRegistry schemaRegistry;

	/**
	 * Compiled schemas, keyed by raw content and bounded to {@link #MAX_CACHED_SCHEMAS}
	 * by insertion order (#831). Synchronized rather than concurrent because an LRU map
	 * cannot be made thread-safe by itself; compilation is once-per-chart and off the hot
	 * path.
	 */
	private final Map<String, Schema> schemaCache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Schema> eldest) {
			return size() > MAX_CACHED_SCHEMAS;
		}

	});

	public SchemaValidator() {
		SchemaRegistryConfig config = SchemaRegistryConfig.builder().build();
		// A chart must never make jhelm fetch a URL while validating: that is the
		// remote-$ref memory-exhaustion vector (GHSA-9h84-qmv7-982p) and an SSRF-shaped
		// surface. Remote fetching is turned off explicitly rather than relying on the
		// library default, and every external IRI is blocked outright, so a $ref can only
		// resolve inside the chart's own schema document (#831).
		this.schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
				(builder) -> builder.schemaRegistryConfig(config)
					.schemaLoader((loader) -> loader.fetchRemoteResources(false).block((iri) -> true)));
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
		// Fail closed on an over-size schema: skipping validation silently would let a
		// hostile chart opt out of its own constraints (#831).
		if (schemaJson.length() > MAX_SCHEMA_BYTES) {
			throw new SchemaValidationException(chartName, List.of("values.schema.json is " + schemaJson.length()
					+ " bytes, over the " + MAX_SCHEMA_BYTES + "-byte limit"));
		}
		Schema schema = compile(chartName, schemaJson);
		if (schema == null) {
			// Malformed schema — already logged; treat as absent, like Helm.
			return;
		}
		// Parse the values through this class's constrained mapper and hand networknt the
		// node: its String overload parses with its own mapper, so the nesting limit
		// would
		// not reach the values at all (#831).
		JsonNode valuesNode;
		try {
			valuesNode = JSON_MAPPER.readTree(JSON_MAPPER.writeValueAsString((values != null) ? values : Map.of()));
		}
		catch (StreamConstraintsException ex) {
			throw new SchemaValidationException(chartName, List.of("values exceed a parser limit: " + ex.getMessage()));
		}
		catch (RuntimeException ex) {
			if (log.isWarnEnabled()) {
				log.warn("Could not serialize values for schema validation of chart {}: {}", chartName,
						ex.getMessage());
			}
			return;
		}
		OutputUnit result = schema.validate(valuesNode, OutputFormat.LIST);
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
		catch (StreamConstraintsException ex) {
			// Over the nesting/length limit — treated as malformed (logged, validation
			// skipped), same as any unparseable schema (#831).
			if (log.isWarnEnabled()) {
				log.warn("values.schema.json for chart {} exceeds a parser limit: {}", chartName, ex.getMessage());
			}
			return null;
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
