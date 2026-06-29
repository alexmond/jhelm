package org.alexmond.jhelm.core.util;

import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.constructor.core.ConstructYamlCoreInt;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.CoreScalarResolver;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;
import org.snakeyaml.engine.v2.schema.CoreSchema;
import org.snakeyaml.engine.v2.schema.Schema;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility for loading Helm values YAML files, including multi-document files.
 * <p>
 * Uses SnakeYAML Engine directly (instead of Jackson YAMLMapper) to ensure YAML anchors
 * and aliases are properly resolved. Jackson's YAML parser does not resolve aliases in
 * untyped deserialization, returning the anchor name as a literal string.
 * <p>
 * A multi-document YAML file contains multiple documents separated by {@code ---}.
 * Documents are merged in order: later documents override earlier ones, with nested maps
 * deep-merged rather than replaced.
 */
public final class ValuesLoader {

	private ValuesLoader() {
	}

	/**
	 * YAML 1.1 boolean tokens, matching Helm's value parser (sigs.k8s.io/yaml -> yaml.v2,
	 * which is YAML 1.1). Helm resolves {@code yes/no/on/off/y/n} (and case variants) as
	 * booleans, not strings — e.g. yugabyte/yugaware's {@code huge_pages: off} renders as
	 * {@code 'false'}. SnakeYAML Engine's core schema (YAML 1.2) only knows true/false,
	 * so jhelm kept the literal strings and diverged.
	 */
	private static final Set<String> YAML11_TRUE = Set.of("y", "Y", "yes", "Yes", "YES", "true", "True", "TRUE", "on",
			"On", "ON");

	private static final Set<String> YAML11_FALSE = Set.of("n", "N", "no", "No", "NO", "false", "False", "FALSE", "off",
			"Off", "OFF");

	private static final Pattern YAML11_BOOL = Pattern
		.compile("^(?:y|Y|yes|Yes|YES|n|N|no|No|NO|true|True|TRUE|false|False|FALSE|on|On|ON|off|Off|OFF)$");

	/**
	 * YAML 1.1 bare-octal literal: a leading zero followed by octal digits (e.g. file
	 * modes {@code 0600}, {@code 0755}). Helm loads values via yaml.v2 (YAML 1.1), which
	 * parses these as octal integers; SnakeYAML Engine's YAML 1.2 core schema would read
	 * {@code 0600} as decimal 600, so a {@code defaultMode: 0600} diverged from Helm's
	 * 384.
	 */
	private static final Pattern YAML11_OCTAL = Pattern.compile("^0[0-7]+$");

	// Declared after the YAML11_* constants it depends on: static fields initialise in
	// source order, so createHelmSchema() must not run before those are set.
	private static final Schema HELM_SCHEMA = createHelmSchema();

	private static Schema createHelmSchema() {
		CoreScalarResolver resolver = new CoreScalarResolver(true);
		// Fix: CoreScalarResolver's NULL first-char is "n\0" but the regex
		// includes ~. Add ~ to the first-char lookup so the resolver checks
		// the NULL regex for tilde values (YAML 1.1/1.2 null literal).
		resolver.addImplicitResolver(Tag.NULL, CoreScalarResolver.NULL, "~");
		// Resolve YAML 1.1 boolean tokens (yes/no/on/off/y/n) as booleans like Helm.
		resolver.addImplicitResolver(Tag.BOOL, YAML11_BOOL, "yYnNtTfFoO");
		// Resolve YAML 1.1 bare-octal literals (0600, 0755) as integers like Helm.
		resolver.addImplicitResolver(Tag.INT, YAML11_OCTAL, "0");
		Map<Tag, ConstructNode> constructors = new HashMap<>(new CoreSchema().getSchemaTagConstructors());
		// The core BOOL constructor only understands true/false; replace it with one that
		// also maps the YAML 1.1 tokens this resolver now tags as BOOL.
		constructors.put(Tag.BOOL, new Yaml11BoolConstructor());
		// The core INT constructor reads a leading-zero literal as decimal; replace it
		// with
		// one that parses YAML 1.1 bare-octal (0600 -> 384) like Helm.
		constructors.put(Tag.INT, new Yaml11OctalIntConstructor());
		return new Schema() {
			@Override
			public ScalarResolver getScalarResolver() {
				return resolver;
			}

			@Override
			public Map<Tag, ConstructNode> getSchemaTagConstructors() {
				return constructors;
			}
		};
	}

	/**
	 * Loads a YAML values file, supporting multi-document files and YAML anchors/aliases.
	 * @param valuesFile the YAML file to load
	 * @return merged values map (empty map if file has no non-null documents)
	 * @throws IOException if the file cannot be read
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> load(File valuesFile) throws IOException {
		LoadSettings settings = LoadSettings.builder().setSchema(HELM_SCHEMA).build();
		Load load = new Load(settings);
		Map<String, Object> merged = new LinkedHashMap<>();
		try (Reader reader = new FileReader(valuesFile, StandardCharsets.UTF_8)) {
			for (Object doc : load.loadAllFromReader(reader)) {
				if (doc instanceof Map) {
					deepMerge(merged, (Map<String, Object>) stringifyKeys(doc));
				}
			}
		}
		return merged;
	}

	/**
	 * Checks whether the given path looks like an HTTP or HTTPS URL.
	 * @param path the file path or URL string
	 * @return {@code true} if the path starts with {@code http://} or {@code https://}
	 */
	public static boolean isUrl(String path) {
		String lower = path.toLowerCase(Locale.ROOT);
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	/**
	 * Downloads YAML content from a URL and parses it as values.
	 * @param url the HTTP/HTTPS URL to download
	 * @return merged values map
	 * @throws IOException if the URL cannot be fetched or parsed
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> loadFromUrl(String url) throws IOException {
		String body;
		try (HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(30))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build()) {
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.GET()
				.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IOException(
						"Failed to fetch values from URL: " + url + " (HTTP " + response.statusCode() + ")");
			}
			body = response.body();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while fetching values from URL: " + url, ex);
		}

		LoadSettings settings = LoadSettings.builder().setSchema(HELM_SCHEMA).build();
		Load load = new Load(settings);
		Map<String, Object> merged = new LinkedHashMap<>();
		try (Reader reader = new StringReader(body)) {
			for (Object doc : load.loadAllFromReader(reader)) {
				if (doc instanceof Map) {
					deepMerge(merged, (Map<String, Object>) stringifyKeys(doc));
				}
			}
		}
		return merged;
	}

	/**
	 * Recursively coerces every map key to a {@link String}. Helm loads values via
	 * sigs.k8s.io/yaml (YAML &rarr; JSON), where object keys are always strings, so a
	 * YAML mapping key written as a bare integer or boolean (e.g. {@code Servers: { 1:
	 * ... }}) becomes the string {@code "1"}. SnakeYAML preserves the scalar's native
	 * type, which would otherwise surface an {@link Integer}/{@link Boolean} key and
	 * break code that (correctly) assumes string keys.
	 * @param value the loaded value tree (map, list, or scalar)
	 * @return the same shape with all map keys stringified
	 */
	static Object stringifyKeys(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> out = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				out.put(String.valueOf(entry.getKey()), stringifyKeys(entry.getValue()));
			}
			return out;
		}
		if (value instanceof List<?> list) {
			List<Object> out = new ArrayList<>(list.size());
			for (Object item : list) {
				out.add(stringifyKeys(item));
			}
			return out;
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	public static void deepMerge(Map<String, Object> base, Map<String, Object> override) {
		for (Map.Entry<String, Object> entry : override.entrySet()) {
			Object overrideVal = entry.getValue();
			Object baseVal = base.get(entry.getKey());
			if ((overrideVal instanceof Map) && (baseVal instanceof Map)) {
				deepMerge((Map<String, Object>) baseVal, (Map<String, Object>) overrideVal);
			}
			else {
				base.put(entry.getKey(), overrideVal);
			}
		}
	}

	/**
	 * Constructs a Boolean from a scalar the {@link #YAML11_BOOL} resolver tagged as
	 * {@code !!bool} — handles the YAML 1.1 tokens (yes/no/on/off/y/n) in addition to
	 * true/false, matching Helm's yaml.v2 value parsing.
	 */
	private static final class Yaml11BoolConstructor implements ConstructNode {

		@Override
		public Object construct(Node node) {
			String value = ((ScalarNode) node).getValue();
			if (YAML11_TRUE.contains(value)) {
				return Boolean.TRUE;
			}
			if (YAML11_FALSE.contains(value)) {
				return Boolean.FALSE;
			}
			// Should not happen — the resolver only tags the tokens above as BOOL.
			return Boolean.parseBoolean(value);
		}

	}

	/**
	 * Integer constructor that recognises YAML 1.1 bare-octal literals ({@code 0600}) in
	 * addition to the YAML 1.2 formats handled by {@link ConstructYamlCoreInt}, so a
	 * leading-zero file mode parses as octal (matching Helm's yaml.v2), not decimal.
	 */
	private static final class Yaml11OctalIntConstructor extends ConstructYamlCoreInt {

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
