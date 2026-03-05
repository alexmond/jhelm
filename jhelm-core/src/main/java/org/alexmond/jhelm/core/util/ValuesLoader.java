package org.alexmond.jhelm.core.util;

import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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

	private static final Schema HELM_SCHEMA = createHelmSchema();

	private ValuesLoader() {
	}

	private static Schema createHelmSchema() {
		CoreScalarResolver resolver = new CoreScalarResolver(true);
		// Fix: CoreScalarResolver's NULL first-char is "n\0" but the regex
		// includes ~. Add ~ to the first-char lookup so the resolver checks
		// the NULL regex for tilde values (YAML 1.1/1.2 null literal).
		resolver.addImplicitResolver(Tag.NULL, CoreScalarResolver.NULL, "~");
		Map<Tag, ConstructNode> constructors = new CoreSchema().getSchemaTagConstructors();
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
					deepMerge(merged, (Map<String, Object>) doc);
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
					deepMerge(merged, (Map<String, Object>) doc);
				}
			}
		}
		return merged;
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

}
