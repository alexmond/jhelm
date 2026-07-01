package org.alexmond.jhelm.core.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Encodes and decodes a {@link Release} to Helm's on-cluster release JSON at the storage
 * boundary (the payload stored, gzipped, inside the {@code sh.helm.release.v1.*} Secret).
 *
 * <p>
 * The top-level release schema is already Helm-compatible via annotations on
 * {@link Release} (snake_case info timestamps, bare {@code config} map,
 * {@code omitempty}, unknown-field tolerance). The embedded {@code chart} needs a further
 * transform that must <em>not</em> touch the in-memory {@link Chart} model — the
 * rendering engine reads template/file content as raw text, and the REST API serializes
 * charts in readable form — so it is applied here, on the JSON tree, only for storage:
 * </p>
 * <ul>
 * <li>{@code templates[].data} is base64-encoded (Helm stores chart file bytes
 * base64);</li>
 * <li>{@code files} is converted between jhelm's {@code {name: content}} map and Helm's
 * {@code [{name, data}]} array with base64 content;</li>
 * <li>the values schema is moved between jhelm's raw {@code valuesSchema} and Helm's
 * base64 {@code schema};</li>
 * <li>subchart {@code dependencies} are transformed recursively.</li>
 * </ul>
 */
public final class HelmReleaseCodec {

	private final JsonMapper mapper = JsonMapper.builder().build();

	/**
	 * Serializes a release to Helm's release-payload JSON.
	 * @param release the release to encode
	 * @return the Helm-shaped JSON bytes
	 */
	public byte[] toJson(Release release) {
		ObjectNode root = (ObjectNode) this.mapper.valueToTree(release);
		if (root.get("chart") instanceof ObjectNode chart) {
			chartToHelm(chart);
		}
		return this.mapper.writeValueAsBytes(root);
	}

	/**
	 * Deserializes a Helm-shaped release payload (as written by {@code helm} or by
	 * {@link #toJson(Release)}) back into a {@link Release}.
	 * @param json the release-payload JSON bytes
	 * @return the decoded release
	 */
	public Release fromJson(byte[] json) {
		JsonNode tree = this.mapper.readTree(json);
		if (tree instanceof ObjectNode root && root.get("chart") instanceof ObjectNode chart) {
			chartFromHelm(chart);
		}
		return this.mapper.treeToValue(tree, Release.class);
	}

	private void chartToHelm(ObjectNode chart) {
		base64DataItems(chart, "templates");
		renameAndBase64(chart, "valuesSchema", "schema");
		filesMapToArray(chart);
		forEachDependency(chart, this::chartToHelm);
	}

	private void chartFromHelm(ObjectNode chart) {
		decodeDataItems(chart, "templates");
		renameAndDecode(chart, "schema", "valuesSchema");
		filesArrayToMap(chart);
		forEachDependency(chart, this::chartFromHelm);
	}

	private void base64DataItems(ObjectNode chart, String field) {
		if (chart.get(field) instanceof ArrayNode items) {
			for (JsonNode item : items) {
				if (item instanceof ObjectNode node && node.get("data") != null && !node.get("data").isNull()) {
					node.put("data", encode(node.get("data").asString()));
				}
			}
		}
	}

	private void decodeDataItems(ObjectNode chart, String field) {
		if (chart.get(field) instanceof ArrayNode items) {
			for (JsonNode item : items) {
				if (item instanceof ObjectNode node && node.get("data") != null && !node.get("data").isNull()) {
					node.put("data", decode(node.get("data").asString()));
				}
			}
		}
	}

	private void renameAndBase64(ObjectNode chart, String from, String to) {
		JsonNode value = chart.get(from);
		if (value != null && !value.isNull()) {
			chart.put(to, encode(value.asString()));
		}
		chart.remove(from);
	}

	private void renameAndDecode(ObjectNode chart, String from, String to) {
		JsonNode value = chart.get(from);
		if (value != null && !value.isNull()) {
			chart.put(to, decode(value.asString()));
		}
		chart.remove(from);
	}

	private void filesMapToArray(ObjectNode chart) {
		if (chart.get("files") instanceof ObjectNode files) {
			ArrayNode array = this.mapper.createArrayNode();
			for (String name : files.propertyNames()) {
				JsonNode content = files.get(name);
				ObjectNode entry = this.mapper.createObjectNode();
				entry.put("name", name);
				entry.put("data", content.isNull() ? "" : encode(content.asString()));
				array.add(entry);
			}
			chart.set("files", array);
		}
	}

	private void filesArrayToMap(ObjectNode chart) {
		if (chart.get("files") instanceof ArrayNode files) {
			ObjectNode map = this.mapper.createObjectNode();
			for (JsonNode item : files) {
				if (item instanceof ObjectNode node && node.get("name") != null) {
					JsonNode data = node.get("data");
					map.put(node.get("name").asString(),
							(data == null || data.isNull()) ? "" : decode(data.asString()));
				}
			}
			chart.set("files", map);
		}
	}

	private void forEachDependency(ObjectNode chart, Consumer<ObjectNode> transform) {
		if (chart.get("dependencies") instanceof ArrayNode dependencies) {
			for (JsonNode dependency : dependencies) {
				if (dependency instanceof ObjectNode node) {
					transform.accept(node);
				}
			}
		}
	}

	private String encode(String raw) {
		return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private String decode(String base64) {
		return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
	}

}
