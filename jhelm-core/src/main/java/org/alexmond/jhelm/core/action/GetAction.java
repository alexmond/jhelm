package org.alexmond.jhelm.core.action;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.util.ValuesLoader;

/**
 * Implements {@code helm get} sub-commands: retrieves a deployed release and extracts its
 * stored manifest, hooks, notes, computed values and metadata. Mirrors the output
 * sections of {@code helm get all}.
 */
@RequiredArgsConstructor
public class GetAction {

	private final KubeService kubeService;

	/**
	 * Returns the latest revision of a named release.
	 * @param name the release name
	 * @param namespace the release namespace
	 * @return the release if found, otherwise empty
	 * @throws Exception if the release store cannot be read
	 */
	public Optional<Release> getRelease(String name, String namespace) throws Exception {
		return kubeService.getRelease(name, namespace);
	}

	/**
	 * Returns a specific revision of a named release.
	 * @param name the release name
	 * @param namespace the release namespace
	 * @param revision the revision number to look up
	 * @return the matching revision if found, otherwise empty
	 * @throws Exception if the release history cannot be read
	 */
	public Optional<Release> getReleaseByRevision(String name, String namespace, int revision) throws Exception {
		List<Release> history = kubeService.getReleaseHistory(name, namespace);
		return history.stream().filter((r) -> r.getVersion() == revision).findFirst();
	}

	/**
	 * Returns the release's values as YAML.
	 * @param release the release to inspect
	 * @param all if {@code true}, return the chart defaults merged with user overrides;
	 * if {@code false}, return only the user-supplied overrides
	 * @return the values rendered as YAML, or {@code "{}"} when there are none
	 * @throws Exception if the values cannot be serialized
	 */
	public String getValues(Release release, boolean all) throws Exception {
		if (all && release.getChart() != null && release.getChart().getValues() != null) {
			Map<String, Object> merged = new LinkedHashMap<>(release.getChart().getValues());
			if (release.getConfig() != null && release.getConfig().getValues() != null) {
				ValuesLoader.deepMerge(merged, release.getConfig().getValues());
			}
			return toYaml(merged);
		}
		if (release.getConfig() == null || release.getConfig().getValues() == null
				|| release.getConfig().getValues().isEmpty()) {
			return "{}";
		}
		return toYaml(release.getConfig().getValues());
	}

	/**
	 * Returns the stored Kubernetes manifest for the release.
	 * @param release the release to inspect
	 * @return the manifest, or an empty string when none is stored
	 */
	public String getManifest(Release release) {
		return (release.getManifest() != null) ? release.getManifest() : "";
	}

	/**
	 * Returns the rendered {@code NOTES.txt} for the release.
	 * @param release the release to inspect
	 * @return the notes, or an empty string when none are stored
	 */
	public String getNotes(Release release) {
		if (release.getInfo() != null && release.getInfo().getNotes() != null) {
			return release.getInfo().getNotes();
		}
		return "";
	}

	/**
	 * Extracts the hook resources from the release manifest (documents annotated with
	 * {@code helm.sh/hook}), joined as a multi-document YAML string.
	 * @param release the release to inspect
	 * @return the hook documents, or an empty string when there are none
	 */
	public String getHooks(Release release) {
		if (release.getManifest() == null || release.getManifest().isEmpty()) {
			return "";
		}
		String[] docs = release.getManifest().split("---");
		StringBuilder hooks = new StringBuilder();
		for (String doc : docs) {
			if (doc.isBlank()) {
				continue;
			}
			if (doc.contains("helm.sh/hook")) {
				if (!hooks.isEmpty()) {
					hooks.append("---\n");
				}
				hooks.append(doc.trim()).append('\n');
			}
		}
		return hooks.toString();
	}

	/**
	 * Builds a summary metadata map for the release (name, namespace, revision, chart
	 * name/version, app version and deployment status).
	 * @param release the release to inspect
	 * @return an ordered map of metadata fields
	 */
	public Map<String, Object> getMetadata(Release release) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("name", release.getName());
		metadata.put("namespace", release.getNamespace());
		metadata.put("revision", release.getVersion());
		if (release.getChart() != null && release.getChart().getMetadata() != null) {
			ChartMetadata cm = release.getChart().getMetadata();
			metadata.put("chart", cm.getName() + "-" + cm.getVersion());
			metadata.put("appVersion", cm.getAppVersion());
		}
		if (release.getInfo() != null) {
			metadata.put("status", release.getInfo().getStatus());
			metadata.put("deployedAt", release.getInfo().getLastDeployed());
		}
		return metadata;
	}

	/**
	 * Combines the manifest, hooks, notes and values into a single {@code helm get all}
	 * style report, with each section under its own header.
	 * @param release the release to inspect
	 * @param allValues if {@code true}, include merged chart defaults in the VALUES
	 * section
	 * @return the combined multi-section report
	 * @throws Exception if the values cannot be serialized
	 */
	public String getAll(Release release, boolean allValues) throws Exception {
		StringBuilder sb = new StringBuilder();

		sb.append("MANIFEST:\n");
		sb.append(getManifest(release));
		if (!getManifest(release).endsWith("\n")) {
			sb.append('\n');
		}

		String hooks = getHooks(release);
		if (!hooks.isEmpty()) {
			sb.append("\nHOOKS:\n");
			sb.append(hooks);
		}

		String notes = getNotes(release);
		if (!notes.isEmpty()) {
			sb.append("\nNOTES:\n");
			sb.append(notes);
			if (!notes.endsWith("\n")) {
				sb.append('\n');
			}
		}

		sb.append("\nVALUES:\n");
		sb.append(getValues(release, allValues));

		return sb.toString();
	}

	/**
	 * Serializes an object to YAML using the action's standard formatting (no document
	 * start marker, minimized quotes, null values omitted).
	 * @param obj the object to serialize
	 * @return the YAML representation
	 * @throws Exception if serialization fails
	 */
	public String toYaml(Object obj) throws Exception {
		YAMLMapper yamlMapper = YAMLMapper.builder()
			.disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
			.enable(YAMLWriteFeature.MINIMIZE_QUOTES)
			.enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
			.changeDefaultPropertyInclusion((v) -> v.withValueInclusion(JsonInclude.Include.NON_NULL)
				.withContentInclusion(JsonInclude.Include.NON_NULL))
			.build();
		return yamlMapper.writeValueAsString(obj);
	}

	/**
	 * Serializes an object to indented JSON.
	 * @param obj the object to serialize
	 * @return the JSON representation
	 * @throws Exception if serialization fails
	 */
	public String toJson(Object obj) throws Exception {
		JsonMapper jsonMapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
		return jsonMapper.writeValueAsString(obj);
	}

}
