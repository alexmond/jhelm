package org.alexmond.jhelm.core.util;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ThreeWayJsonMerge} — the cluster-free heart of the upgrade
 * field-pruning fix (#814). Each case builds original/modified/current as JSON and
 * asserts the produced RFC 7386 merge patch, and (where it matters) the result of
 * applying that patch to the live object.
 */
class ThreeWayJsonMergeTest {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static JsonNode json(String text) {
		return MAPPER.readTree(text);
	}

	private static ObjectNode patch(String original, String modified, String current) {
		return ThreeWayJsonMerge.threeWayMergePatch(json(original), json(modified), json(current));
	}

	/**
	 * Applies an RFC 7386 merge patch to a document, so tests can assert the end state.
	 */
	private static JsonNode apply(String doc, JsonNode mergePatch) {
		return applyNode(json(doc), mergePatch);
	}

	private static JsonNode applyNode(JsonNode target, JsonNode patch) {
		if (!patch.isObject()) {
			return patch;
		}
		ObjectNode base = target.isObject() ? (ObjectNode) target.deepCopy() : MAPPER.createObjectNode();
		for (var entry : patch.properties()) {
			JsonNode value = entry.getValue();
			if (value.isNull()) {
				base.remove(entry.getKey());
			}
			else if (value.isObject()) {
				base.set(entry.getKey(), applyNode(base.get(entry.getKey()), value));
			}
			else {
				base.set(entry.getKey(), value);
			}
		}
		return base;
	}

	@Test
	void removedEnvEntryIsPrunedByReplacingTheWholeArray() {
		// The #814 reproduction: env [A,B] -> [A]; B must be gone from the live object.
		String original = """
				{"spec":{"env":[{"name":"A","value":"1"},{"name":"B","value":"2"}]}}""";
		String modified = """
				{"spec":{"env":[{"name":"A","value":"1"}]}}""";
		String current = original;
		ObjectNode p = patch(original, modified, current);
		JsonNode result = apply(current, p);
		assertEquals(json(modified), result, "removed env entry B must not survive the upgrade");
		assertEquals(1, result.get("spec").get("env").size());
	}

	@Test
	void wholeEnvRemovalEmitsExplicitNullDeletion() {
		String original = """
				{"spec":{"env":[{"name":"A","value":"1"}]}}""";
		String modified = """
				{"spec":{}}""";
		ObjectNode p = patch(original, modified, original);
		assertTrue(p.path("spec").path("env").isNull(), "dropped env key must become an explicit null: " + p);
		JsonNode result = apply(original, p);
		assertFalse(result.get("spec").has("env"), "env must be deleted");
	}

	@Test
	void topLevelKeyDroppedByReleaseIsDeleted() {
		String original = """
				{"metadata":{"labels":{"keep":"yes","drop":"old"}}}""";
		String modified = """
				{"metadata":{"labels":{"keep":"yes"}}}""";
		ObjectNode p = patch(original, modified, original);
		assertTrue(p.path("metadata").path("labels").path("drop").isNull(), "dropped label must be null: " + p);
		JsonNode result = apply(original, p);
		assertFalse(result.get("metadata").get("labels").has("drop"));
		assertEquals("yes", result.get("metadata").get("labels").get("keep").asString());
	}

	@Test
	void controllerSetFieldNotManagedByReleaseIsPreserved() {
		// clusterIP is set by the API server; it is in neither original nor modified.
		String original = """
				{"spec":{"ports":[{"port":80}]}}""";
		String modified = """
				{"spec":{"ports":[{"port":80}]}}""";
		String current = """
				{"spec":{"ports":[{"port":80}],"clusterIP":"192.0.2.5"}}""";
		ObjectNode p = patch(original, modified, current);
		assertFalse(p.path("spec").has("clusterIP"), "must not touch a field the release never managed: " + p);
		JsonNode result = apply(current, p);
		assertEquals("192.0.2.5", result.get("spec").get("clusterIP").asString());
	}

	@Test
	void identicalStatesProduceEmptyPatch() {
		String doc = """
				{"spec":{"replicas":3,"env":[{"name":"A","value":"1"}]}}""";
		ObjectNode p = patch(doc, doc, doc);
		assertTrue(p.isEmpty(), "no-op upgrade must produce an empty patch: " + p);
	}

	@Test
	void changedScalarIsMeasuredAgainstLiveObject() {
		String original = """
				{"spec":{"replicas":2}}""";
		String modified = """
				{"spec":{"replicas":3}}""";
		String current = """
				{"spec":{"replicas":2}}""";
		ObjectNode p = patch(original, modified, current);
		assertEquals(3, p.get("spec").get("replicas").asInt());
	}

	@Test
	void fieldAlreadyAtDesiredValueOnClusterIsNotResent() {
		// modified == current for replicas; only original differs -> nothing to
		// add/change.
		String original = """
				{"spec":{"replicas":2}}""";
		String modified = """
				{"spec":{"replicas":3}}""";
		String current = """
				{"spec":{"replicas":3}}""";
		ObjectNode p = patch(original, modified, current);
		assertTrue(p.isEmpty(), "already-correct field must not be re-sent: " + p);
	}

	@Test
	void releaseReassertsItsValueWhenDriftedOnCluster() {
		// The release owns replicas (unchanged original==modified) but the cluster
		// drifted.
		String original = """
				{"spec":{"replicas":3}}""";
		String modified = """
				{"spec":{"replicas":3}}""";
		String current = """
				{"spec":{"replicas":5}}""";
		ObjectNode p = patch(original, modified, current);
		assertEquals(3, p.get("spec").get("replicas").asInt(), "release value must win over drift");
	}

	@Test
	void nullOriginalPrunesNothing() {
		// No previous manifest: we cannot know what the release dropped, so no deletions.
		String modified = """
				{"spec":{"replicas":3}}""";
		String current = """
				{"spec":{"replicas":2,"env":[{"name":"A","value":"1"}]}}""";
		ObjectNode p = ThreeWayJsonMerge.threeWayMergePatch(null, json(modified), json(current));
		assertEquals(3, p.get("spec").get("replicas").asInt());
		assertFalse(p.path("spec").path("env").isNull(), "must not delete env when original is unknown: " + p);
	}

	@Test
	void newFieldAddedByReleaseIsIncluded() {
		String original = """
				{"spec":{}}""";
		String modified = """
				{"spec":{"paused":true}}""";
		String current = """
				{"spec":{}}""";
		ObjectNode p = patch(original, modified, current);
		assertTrue(p.get("spec").get("paused").asBoolean());
	}

}
