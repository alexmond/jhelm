package org.alexmond.jhelm.core.util;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for building value override maps from {@code -f}/ {@code --values} files and
 * {@code --set key=value} arguments.
 * <p>
 * Files are loaded in order using {@link ValuesLoader} (which supports multi-document
 * YAML). {@code --set} arguments are parsed last so they take precedence over file
 * values. Dot-separated keys create nested maps (e.g. {@code outer.inner=v} produces
 * {@code {outer: {inner: "v"}}}).
 */
public final class ValuesOverrides {

	private ValuesOverrides() {
	}

	/**
	 * Returns the given values map, or an empty map if {@code null}. Convenience method
	 * to avoid repeated null-coalescing in controllers.
	 * @param values the values map (may be {@code null})
	 * @return the values map, or {@code Map.of()} if {@code null}
	 */
	public static Map<String, Object> safeValues(Map<String, Object> values) {
		return (values != null) ? values : Map.of();
	}

	/**
	 * Build a merged override map from values files and {@code --set} arguments.
	 * @param files paths to YAML values files; {@code null} or empty means none
	 * @param setArgs {@code key=value} strings; {@code null} or empty means none
	 * @return merged override map
	 * @throws Exception if any values file cannot be read
	 */
	public static Map<String, Object> parse(List<String> files, List<String> setArgs) throws Exception {
		Map<String, Object> merged = new HashMap<>();
		if (files != null) {
			for (String path : files) {
				Map<String, Object> fileValues;
				if (ValuesLoader.isUrl(path)) {
					fileValues = ValuesLoader.loadFromUrl(path);
				}
				else {
					fileValues = ValuesLoader.load(new File(path));
				}
				ValuesLoader.deepMerge(merged, fileValues);
			}
		}
		if (setArgs != null) {
			for (String arg : setArgs) {
				applySet(merged, arg);
			}
		}
		return merged;
	}

	/**
	 * Parse a single {@code key=value} set argument and apply it to {@code target}.
	 * <p>
	 * Keys may use dot notation to set nested values (e.g. {@code a.b=v}).
	 * @param target the map to merge into
	 * @param arg the {@code key=value} string
	 */
	@SuppressWarnings("unchecked")
	static void applySet(Map<String, Object> target, String arg) {
		int eq = arg.indexOf('=');
		if (eq < 0) {
			return;
		}
		String keyPath = arg.substring(0, eq);
		String value = arg.substring(eq + 1);
		String[] parts = keyPath.split("\\.", -1);
		Map<String, Object> current = target;
		for (int i = 0; i < parts.length - 1; i++) {
			Object existing = current.get(parts[i]);
			if (existing instanceof Map) {
				current = (Map<String, Object>) existing;
			}
			else {
				Map<String, Object> nested = new HashMap<>();
				current.put(parts[i], nested);
				current = nested;
			}
		}
		current.put(parts[parts.length - 1], value);
	}

}
