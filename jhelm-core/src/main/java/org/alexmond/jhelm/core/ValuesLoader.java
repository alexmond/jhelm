package org.alexmond.jhelm.core;

import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for loading Helm values YAML files, including multi-document files.
 * <p>
 * A multi-document YAML file contains multiple documents separated by {@code ---}.
 * Documents are merged in order: later documents override earlier ones, with nested maps
 * deep-merged rather than replaced.
 */
public final class ValuesLoader {

	private ValuesLoader() {
	}

	/**
	 * Loads a YAML values file, supporting multi-document files.
	 * @param valuesFile the YAML file to load
	 * @return merged values map (empty map if file has no non-null documents)
	 * @throws IOException if the file cannot be read
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> load(File valuesFile) throws IOException {
		YAMLMapper mapper = YAMLMapper.builder().build();
		Map<String, Object> merged = new HashMap<>();
		try (MappingIterator<Object> it = mapper.readerFor(Object.class).readValues(valuesFile)) {
			while (it.hasNext()) {
				Object doc = it.next();
				if (doc instanceof Map) {
					deepMerge(merged, (Map<String, Object>) doc);
				}
			}
		}
		return merged;
	}

	@SuppressWarnings("unchecked")
	static void deepMerge(Map<String, Object> base, Map<String, Object> override) {
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
