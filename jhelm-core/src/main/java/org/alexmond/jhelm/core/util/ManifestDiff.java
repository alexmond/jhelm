package org.alexmond.jhelm.core.util;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Computes the set of resources that exist in an old rendered manifest but no longer
 * exist in a new one. Mirrors Helm's upgrade behaviour, which deletes resources that a
 * previous release contained but the new revision no longer renders.
 */
public final class ManifestDiff {

	private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();

	private ManifestDiff() {
	}

	/**
	 * Returns the documents from {@code oldManifest} whose resource identity is not
	 * present in {@code newManifest}, rejoined with {@code ---} the same way
	 * {@link HookParser#stripHooks(String)} joins documents.
	 * <p>
	 * Each manifest is split on {@code ---} and every non-blank document is parsed into a
	 * map. A document is skipped when it is blank, parses to {@code null}, has no
	 * {@code kind}, or has no {@code metadata.name}. The identity of a resource is
	 * {@code apiVersion + "|" + kind + "|" + namespace + "|" + name}, where
	 * {@code namespace} is {@code metadata.namespace} or the empty string when absent.
	 * The original document text is preserved verbatim for each old resource so that
	 * orphans can be deleted exactly as they were applied.
	 * @param oldManifest the previous revision's manifest (may be {@code null} or blank)
	 * @param newManifest the new revision's manifest (may be {@code null} or blank)
	 * @return the orphaned documents rejoined with {@code ---}, or an empty string when
	 * there are no orphans
	 */
	public static String orphanedResources(String oldManifest, String newManifest) {
		Map<String, String> oldResources = parse(oldManifest);
		if (oldResources.isEmpty()) {
			return "";
		}
		Map<String, String> newResources = parse(newManifest);

		StringBuilder result = new StringBuilder();
		for (Map.Entry<String, String> entry : oldResources.entrySet()) {
			if (!newResources.containsKey(entry.getKey())) {
				result.append("---\n").append(entry.getValue().trim()).append('\n');
			}
		}
		return result.toString();
	}

	/**
	 * Parses a manifest into a map of resource identity to the original document text.
	 * @param manifest the manifest to parse (may be {@code null} or blank)
	 * @return a map from resource identity to verbatim document text
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, String> parse(String manifest) {
		Map<String, String> resources = new LinkedHashMap<>();
		if (manifest == null || manifest.isBlank()) {
			return resources;
		}

		String[] docs = manifest.split("---");
		for (String doc : docs) {
			if (doc.isBlank()) {
				continue;
			}
			try {
				Map<String, Object> parsed = YAML_MAPPER.readValue(doc, Map.class);
				if (parsed == null) {
					continue;
				}
				String kind = (String) parsed.get("kind");
				if (kind == null) {
					continue;
				}
				Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
				String name = (metadata != null) ? (String) metadata.get("name") : null;
				if (name == null) {
					continue;
				}
				String apiVersion = (String) parsed.get("apiVersion");
				String namespace = (metadata.get("namespace") != null) ? metadata.get("namespace").toString() : "";
				String identity = apiVersion + "|" + kind + "|" + namespace + "|" + name;
				resources.put(identity, doc);
			}
			catch (Exception ignored) {
			}
		}
		return resources;
	}

}
