package org.alexmond.jhelm.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import tools.jackson.dataformat.yaml.YAMLMapper;
import org.alexmond.jhelm.core.model.HelmHook;

/**
 * Parses Helm hook resources from rendered chart manifests.
 */
public final class HookParser {

	private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();

	private HookParser() {
	}

	/**
	 * Parses all hook resources from a rendered manifest.
	 * @param manifest the full rendered YAML manifest (may be null or blank)
	 * @return list of parsed hooks, empty if none found
	 */
	@SuppressWarnings("unchecked")
	public static List<HelmHook> parseHooks(String manifest) {
		if (manifest == null || manifest.isBlank()) {
			return Collections.emptyList();
		}

		List<HelmHook> hooks = new ArrayList<>();
		String[] docs = manifest.split("---");

		for (String doc : docs) {
			if (doc.isBlank() || !doc.contains("helm.sh/hook")) {
				continue;
			}

			try {
				Map<String, Object> parsed = YAML_MAPPER.readValue(doc, Map.class);
				if (parsed == null) {
					continue;
				}

				Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
				if (metadata == null) {
					continue;
				}

				Map<String, Object> annotations = (Map<String, Object>) metadata.get("annotations");
				if (annotations == null || !annotations.containsKey("helm.sh/hook")) {
					continue;
				}

				String hookValue = (String) annotations.get("helm.sh/hook");
				List<String> phases = Arrays.asList(hookValue.split(","));

				int weight = 0;
				Object weightObj = annotations.get("helm.sh/hook-weight");
				if (weightObj != null) {
					try {
						weight = Integer.parseInt(weightObj.toString().trim());
					}
					catch (NumberFormatException ignored) {
					}
				}

				List<String> deletePolicy = Collections.emptyList();
				Object policyObj = annotations.get("helm.sh/hook-delete-policy");
				if (policyObj != null) {
					deletePolicy = Arrays.asList(policyObj.toString().split(","));
				}

				String kind = (String) parsed.get("kind");
				String name = (String) metadata.get("name");
				String namespace = (String) metadata.get("namespace");

				hooks.add(HelmHook.builder()
					.kind(kind)
					.name(name)
					.namespace(namespace)
					.phases(phases)
					.weight(weight)
					.deletePolicy(deletePolicy)
					.yaml(doc.trim())
					.build());
			}
			catch (Exception ignored) {
			}
		}

		return hooks;
	}

	/**
	 * Returns only the non-hook documents from a manifest, rejoined with {@code ---}.
	 * @param manifest the full rendered YAML manifest (may be null or blank)
	 * @return manifest with hook documents removed
	 */
	public static String stripHooks(String manifest) {
		if (manifest == null) {
			return "";
		}
		if (manifest.isBlank()) {
			return manifest;
		}

		String[] docs = manifest.split("---");
		StringBuilder result = new StringBuilder();

		for (String doc : docs) {
			if (doc.isBlank() || doc.contains("helm.sh/hook")) {
				continue;
			}
			result.append("---\n").append(doc.trim()).append('\n');
		}

		return result.toString();
	}

	/**
	 * Strips resources annotated with {@code helm.sh/resource-policy: keep} from the
	 * manifest. These resources should be preserved during uninstall.
	 * @param manifest the YAML manifest (may be null or blank)
	 * @return manifest with kept resources removed
	 */
	public static String stripKeptResources(String manifest) {
		if (manifest == null) {
			return "";
		}
		if (manifest.isBlank()) {
			return manifest;
		}

		String[] docs = manifest.split("---");
		StringBuilder result = new StringBuilder();

		for (String doc : docs) {
			if (doc.isBlank()) {
				continue;
			}
			if (doc.contains("helm.sh/resource-policy") && doc.contains("keep")) {
				continue;
			}
			result.append("---\n").append(doc.trim()).append('\n');
		}

		return result.toString();
	}

}
