package org.alexmond.jhelm.core.output;

import java.util.LinkedHashMap;
import java.util.Map;

import org.alexmond.jhelm.core.model.Release;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * Shared machine-readable output helpers for {@code -o json|yaml}, matching Helm's
 * {@code helm ... -o} formats. Centralizes the JSON/YAML mappers and the Helm-shaped
 * (snake_case) release maps so every command renders identically.
 */
public final class OutputFormat {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private static final YAMLMapper YAML_MAPPER = YAMLMapper
		.builder(YAMLFactory.builder().disable(YAMLWriteFeature.WRITE_DOC_START_MARKER).build())
		.build();

	private OutputFormat() {
	}

	/**
	 * @param output the requested output format
	 * @return {@code true} if it is {@code json}
	 */
	public static boolean isJson(String output) {
		return "json".equalsIgnoreCase(output);
	}

	/**
	 * @param output the requested output format
	 * @return {@code true} if it is {@code yaml}
	 */
	public static boolean isYaml(String output) {
		return "yaml".equalsIgnoreCase(output);
	}

	/**
	 * Serializes a value as JSON.
	 * @param value the value to serialize
	 * @return the JSON string
	 */
	public static String json(Object value) {
		return JSON_MAPPER.writeValueAsString(value);
	}

	/**
	 * Serializes a value as YAML (no leading {@code ---} document marker).
	 * @param value the value to serialize
	 * @return the YAML string
	 */
	public static String yaml(Object value) {
		return YAML_MAPPER.writeValueAsString(value);
	}

	/**
	 * Builds a Helm-shaped release map ({@code helm status/get -o json} form), with a
	 * nested {@code info} object and the rendered manifest.
	 * @param release the release
	 * @return an ordered, snake_case map mirroring Helm's release JSON
	 */
	public static Map<String, Object> release(Release release) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("name", release.getName());
		map.put("namespace", release.getNamespace());
		map.put("version", release.getVersion());
		Release.ReleaseInfo info = release.getInfo();
		if (info != null) {
			Map<String, Object> infoMap = new LinkedHashMap<>();
			infoMap.put("first_deployed", (info.getFirstDeployed() != null) ? info.getFirstDeployed().toString() : "");
			infoMap.put("last_deployed", (info.getLastDeployed() != null) ? info.getLastDeployed().toString() : "");
			infoMap.put("deleted", (info.getDeleted() != null) ? info.getDeleted().toString() : "");
			infoMap.put("description", (info.getDescription() != null) ? info.getDescription() : "");
			infoMap.put("status", (info.getStatus() != null) ? info.getStatus().getValue() : "");
			infoMap.put("notes", (info.getNotes() != null) ? info.getNotes() : "");
			map.put("info", infoMap);
		}
		map.put("manifest", (release.getManifest() != null) ? release.getManifest() : "");
		return map;
	}

	/**
	 * Builds a Helm-shaped list-row map ({@code helm list -o json} form).
	 * @param release the release
	 * @return an ordered, snake_case map mirroring Helm's list JSON entries
	 */
	public static Map<String, Object> listRow(Release release) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("name", release.getName());
		row.put("namespace", release.getNamespace());
		row.put("revision", release.getVersion());
		Release.ReleaseInfo info = release.getInfo();
		row.put("updated", (info != null && info.getLastDeployed() != null) ? info.getLastDeployed().toString() : "");
		row.put("status", (info != null && info.getStatus() != null) ? info.getStatus().getValue() : "");
		row.put("chart",
				release.getChart().getMetadata().getName() + "-" + release.getChart().getMetadata().getVersion());
		String appVersion = release.getChart().getMetadata().getAppVersion();
		row.put("app_version", (appVersion != null) ? appVersion : "");
		return row;
	}

	/**
	 * Builds a Helm-shaped history-row map ({@code helm history -o json} form).
	 * @param release the release revision
	 * @return an ordered, snake_case map mirroring Helm's history JSON entries
	 */
	public static Map<String, Object> historyRow(Release release) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("revision", release.getVersion());
		Release.ReleaseInfo info = release.getInfo();
		map.put("updated", (info != null && info.getLastDeployed() != null) ? info.getLastDeployed().toString() : "");
		map.put("status", (info != null && info.getStatus() != null) ? info.getStatus().getValue() : "");
		map.put("chart",
				release.getChart().getMetadata().getName() + "-" + release.getChart().getMetadata().getVersion());
		String appVersion = release.getChart().getMetadata().getAppVersion();
		map.put("app_version", (appVersion != null) ? appVersion : "");
		map.put("description", (info != null && info.getDescription() != null) ? info.getDescription() : "");
		return map;
	}

}
