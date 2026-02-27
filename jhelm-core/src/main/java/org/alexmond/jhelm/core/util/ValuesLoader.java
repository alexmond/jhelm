package org.alexmond.jhelm.core.util;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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

	private ValuesLoader() {
	}

	/**
	 * Loads a YAML values file, supporting multi-document files and YAML anchors/aliases.
	 * @param valuesFile the YAML file to load
	 * @return merged values map (empty map if file has no non-null documents)
	 * @throws IOException if the file cannot be read
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> load(File valuesFile) throws IOException {
		LoadSettings settings = LoadSettings.builder().build();
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
