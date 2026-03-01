package org.alexmond.jhelm.core.model;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements Helm's .Files template object. Provides access to non-template files within
 * a chart archive.
 *
 * <p>
 * Method names use PascalCase to match Go/Helm convention (e.g. {@code .Files.Get},
 * {@code .Files.Glob}). The template executor resolves methods by exact name via
 * reflection.
 *
 * @see <a href="https://helm.sh/docs/chart_template_guide/accessing_files/">Helm File
 * Access</a>
 */
@SuppressWarnings("PMD.MethodNamingConventions")
public class ChartFiles {

	private final Map<String, String> files;

	public ChartFiles(Map<String, String> files) {
		this.files = (files != null) ? files : Map.of();
	}

	/**
	 * Returns files matching the given glob pattern as a map of path to content.
	 * @param pattern glob pattern (e.g. "files/crds/*.yaml")
	 * @return map of matching file paths to their content
	 */
	public Map<String, String> Glob(String pattern) {
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
		Map<String, String> result = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : files.entrySet()) {
			if (matcher.matches(Paths.get(entry.getKey()))) {
				result.put(entry.getKey(), entry.getValue());
			}
		}
		return result;
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
	 * Returns all files with their content base64-encoded (for use in Secret data).
	 * @return map of file paths to base64-encoded content
	 */
	public Map<String, String> AsSecrets() {
		Map<String, String> result = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : files.entrySet()) {
			result.put(entry.getKey(),
					Base64.getEncoder().encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8)));
		}
		return result;
	}

	/**
	 * Returns all files with their raw string content (for use in ConfigMap data).
	 * @return map of file paths to string content
	 */
	public Map<String, String> AsConfig() {
		return new LinkedHashMap<>(files);
	}

	@Override
	public String toString() {
		return "";
	}

}
