package org.alexmond.jhelm.core.model;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * Implements Helm's .Files template object. Provides access to non-template files within
 * a chart archive.
 *
 * <p>
 * Like Helm's {@code files} type, this is a map (path → content) that <em>also</em>
 * exposes helper methods. So a template can both {@code range} over it and call
 * {@code .Get}/{@code .Glob}/{@code .AsConfig}. Method names use PascalCase to match
 * Go/Helm convention; the template executor resolves them by exact name via reflection
 * (falling back from map-key lookup).
 *
 * @see <a href="https://helm.sh/docs/chart_template_guide/accessing_files/">Helm File
 * Access</a>
 */
@SuppressWarnings("PMD.MethodNamingConventions")
public class ChartFiles extends AbstractMap<String, String> {

	private static final YAMLMapper YAML = YAMLMapper.builder()
		.disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
		.enable(YAMLWriteFeature.MINIMIZE_QUOTES)
		.build();

	private final Map<String, String> files;

	public ChartFiles(Map<String, String> files) {
		this.files = (files != null) ? files : Map.of();
	}

	@Override
	public Set<Map.Entry<String, String>> entrySet() {
		return files.entrySet();
	}

	/**
	 * Returns files matching the given glob pattern. Like Helm, the result is itself a
	 * {@link ChartFiles} so chained calls such as {@code .AsConfig} work.
	 * @param pattern glob pattern (e.g. "files/crds/*.yaml")
	 * @return a ChartFiles containing only the matching files
	 */
	public ChartFiles Glob(String pattern) {
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
		Map<String, String> result = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : files.entrySet()) {
			if (matcher.matches(Paths.get(entry.getKey()))) {
				result.put(entry.getKey(), entry.getValue());
			}
		}
		return new ChartFiles(result);
	}

	/**
	 * Returns the content of a file as a string.
	 * @param name the file path
	 * @return file content or empty string if not found
	 */
	public String Get(String name) {
		return files.getOrDefault(name, "");
	}

	/**
	 * Returns the content of a file as a byte array.
	 * @param name the file path
	 * @return file content as bytes or empty array if not found
	 */
	public byte[] GetBytes(String name) {
		String content = files.get(name);
		return (content != null) ? content.getBytes(StandardCharsets.UTF_8) : new byte[0];
	}

	/**
	 * Returns the content of a file split into lines.
	 * @param name the file path
	 * @return list of lines or empty list if not found
	 */
	public List<String> Lines(String name) {
		String content = files.get(name);
		if (content == null || content.isEmpty()) {
			return List.of();
		}
		return Arrays.asList(content.split("\n", -1));
	}

	/**
	 * Returns the files as a YAML string for use in Secret {@code data}, keyed by base
	 * file name with base64-encoded values (matching Helm's {@code .AsSecrets}).
	 * @return YAML string of base name → base64 content
	 */
	public String AsSecrets() {
		Map<String, String> result = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : files.entrySet()) {
			result.put(baseName(entry.getKey()),
					Base64.getEncoder().encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8)));
		}
		return toYaml(result);
	}

	/**
	 * Returns the files as a YAML string for use in ConfigMap {@code data}, keyed by base
	 * file name with raw string values (matching Helm's {@code .AsConfig}).
	 * @return YAML string of base name → content
	 */
	public String AsConfig() {
		Map<String, String> result = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : files.entrySet()) {
			result.put(baseName(entry.getKey()), entry.getValue());
		}
		return toYaml(result);
	}

	private static String baseName(String path) {
		int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		return (slash >= 0) ? path.substring(slash + 1) : path;
	}

	private static String toYaml(Map<String, String> map) {
		if (map.isEmpty()) {
			return "";
		}
		try {
			String yaml = YAML.writeValueAsString(map);
			// Helm's toYAML trims the trailing newline; the template handles indentation.
			return yaml.endsWith("\n") ? yaml.substring(0, yaml.length() - 1) : yaml;
		}
		catch (Exception ex) {
			return "";
		}
	}

	@Override
	public String toString() {
		return "";
	}

}
