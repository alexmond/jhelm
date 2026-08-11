package org.alexmond.jhelm.core.util;

import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Computes an <a href="https://www.rfc-editor.org/rfc/rfc7386">RFC 7386 JSON merge
 * patch</a> that upgrades a live Kubernetes object toward a newly rendered manifest
 * <em>while pruning fields the release no longer declares</em>.
 * <p>
 * This mirrors Helm's three-way merge for unstructured resources
 * ({@code jsonmergepatch.CreateThreeWayJSONMergePatch}). Given three states of a single
 * resource:
 * <ul>
 * <li><b>original</b> — the resource as the <em>previous</em> release rendered it (from
 * the stored manifest),</li>
 * <li><b>modified</b> — the resource as the <em>new</em> release renders it,</li>
 * <li><b>current</b> — the resource as it currently lives on the cluster.</li>
 * </ul>
 * the returned patch:
 * <ul>
 * <li>adds/changes every field where {@code current} differs from {@code modified}
 * (computed against the live object so already-correct fields are not re-sent), and</li>
 * <li>deletes only fields present in {@code original} but absent from {@code modified} —
 * i.e. fields the release itself dropped since the last apply — expressed as explicit
 * {@code null}s. Fields that live only on the cluster (controller-set defaults jhelm
 * never managed) are left untouched.</li>
 * </ul>
 * Because JSON merge patch treats arrays atomically, a list whose contents changed (for
 * example a container {@code env} array with an entry removed) is replaced wholesale, so
 * removed entries are pruned — which server-side apply, keyed on prior field ownership,
 * does not guarantee.
 */
public final class ThreeWayJsonMerge {

	private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

	private ThreeWayJsonMerge() {
	}

	/**
	 * Builds the three-way JSON merge patch for a single resource. A {@code null}
	 * argument is treated as an empty object.
	 * @param original the resource as the previous release rendered it (may be
	 * {@code null} when no prior manifest is available — then nothing is pruned)
	 * @param modified the resource as the new release renders it
	 * @param current the resource as it currently lives on the cluster
	 * @return an RFC 7386 merge patch; an empty object when the live resource already
	 * matches and nothing is pruned
	 */
	public static ObjectNode threeWayMergePatch(JsonNode original, JsonNode modified, JsonNode current) {
		JsonNode orig = objectOrEmpty(original);
		JsonNode mod = objectOrEmpty(modified);
		JsonNode cur = objectOrEmpty(current);
		// Additions/changes are measured against the LIVE object, so fields already at
		// the
		// desired value are not re-sent; nulls (fields on the cluster but not in
		// modified)
		// are dropped here — they are handled by the deletion pass, and only when the
		// release itself owned them.
		JsonNode addAndChange = filterNulls(createMergePatch(cur, mod), false);
		// Deletions are measured against what the PREVIOUS release rendered, so only
		// fields
		// the release dropped are removed — never controller-set fields jhelm never
		// managed.
		JsonNode deletions = filterNulls(createMergePatch(orig, mod), true);
		return union(deletions, addAndChange);
	}

	/**
	 * Computes the RFC 7386 merge patch that turns {@code source} into {@code target}.
	 * Objects are diffed recursively; arrays and scalars are atomic (a change emits the
	 * whole target value); a key present in {@code source} but not {@code target} emits
	 * an explicit {@code null}.
	 */
	static ObjectNode createMergePatch(JsonNode source, JsonNode target) {
		ObjectNode patch = NODES.objectNode();
		for (Map.Entry<String, JsonNode> entry : target.properties()) {
			String key = entry.getKey();
			JsonNode targetValue = entry.getValue();
			JsonNode sourceValue = source.get(key);
			if (sourceValue == null) {
				patch.set(key, targetValue);
			}
			else if (!sourceValue.equals(targetValue)) {
				if (sourceValue.isObject() && targetValue.isObject()) {
					ObjectNode sub = createMergePatch(sourceValue, targetValue);
					if (!sub.isEmpty()) {
						patch.set(key, sub);
					}
				}
				else {
					patch.set(key, targetValue);
				}
			}
		}
		for (String key : source.propertyNames()) {
			if (!target.has(key)) {
				patch.set(key, NullNode.getInstance());
			}
		}
		return patch;
	}

	/**
	 * Recursively keeps or drops {@code null}-valued leaves of a merge patch. With
	 * {@code keepNull} {@code true} only the {@code null} deletions survive; with
	 * {@code false} only the additions/changes survive. Objects that become empty after
	 * filtering are dropped.
	 */
	static ObjectNode filterNulls(JsonNode patch, boolean keepNull) {
		ObjectNode result = NODES.objectNode();
		for (Map.Entry<String, JsonNode> entry : patch.properties()) {
			String key = entry.getKey();
			JsonNode value = entry.getValue();
			if (value.isNull()) {
				if (keepNull) {
					result.set(key, NullNode.getInstance());
				}
			}
			else if (value.isObject()) {
				ObjectNode sub = filterNulls(value, keepNull);
				if (!sub.isEmpty()) {
					result.set(key, sub);
				}
			}
			else if (!keepNull) {
				result.set(key, value);
			}
		}
		return result;
	}

	/**
	 * Deep-unions two disjoint merge-patch documents, preserving {@code null} deletions
	 * from {@code deletions}. The two inputs never touch the same leaf (a field cannot be
	 * both dropped by the release and required by it), so a straight recursive union is
	 * sufficient.
	 */
	static ObjectNode union(JsonNode deletions, JsonNode addAndChange) {
		ObjectNode result = (ObjectNode) deletions.deepCopy();
		for (Map.Entry<String, JsonNode> entry : addAndChange.properties()) {
			String key = entry.getKey();
			JsonNode value = entry.getValue();
			JsonNode existing = result.get(key);
			if (existing != null && existing.isObject() && value.isObject()) {
				result.set(key, union(existing, value));
			}
			else {
				result.set(key, value);
			}
		}
		return result;
	}

	private static JsonNode objectOrEmpty(JsonNode node) {
		return (node != null && node.isObject()) ? node : NODES.objectNode();
	}

}
