package org.alexmond.jhelm.core.util;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderedManifestTest {

	private static final String MANIFEST = """
			# Source: mychart/templates/configmap.yaml
			apiVersion: v1
			kind: ConfigMap
			metadata:
			  name: cm
			---
			# Source: mychart/templates/deployment.yaml
			apiVersion: apps/v1
			kind: Deployment
			metadata:
			  name: dep
			---
			# Source: mychart/templates/tests/test-connection.yaml
			apiVersion: v1
			kind: Pod
			metadata:
			  name: test
			  annotations:
			    "helm.sh/hook": test
			""";

	@Test
	void testParseAttributesEachDocumentToItsSource() {
		List<RenderedManifest.Document> docs = RenderedManifest.parse(MANIFEST);
		assertEquals(3, docs.size());
		assertEquals("mychart/templates/configmap.yaml", docs.get(0).source());
		assertEquals("mychart/templates/deployment.yaml", docs.get(1).source());
		assertEquals("mychart/templates/tests/test-connection.yaml", docs.get(2).source());
	}

	@Test
	void testParseCarriesSourceForwardToUnmarkedDocuments() {
		// A template that emits two documents gets one marker; the second inherits it.
		String multi = """
				# Source: c/templates/multi.yaml
				kind: A
				---
				kind: B
				""";
		List<RenderedManifest.Document> docs = RenderedManifest.parse(multi);
		assertEquals(2, docs.size());
		assertEquals("c/templates/multi.yaml", docs.get(0).source());
		assertEquals("c/templates/multi.yaml", docs.get(1).source());
	}

	@Test
	void testShowOnlyMatchesByTrailingPathSegment() {
		String out = RenderedManifest.showOnly(MANIFEST, List.of("templates/deployment.yaml"));
		assertTrue(out.contains("kind: Deployment"), out);
		assertFalse(out.contains("kind: ConfigMap"), out);
		assertTrue(out.contains("# Source: mychart/templates/deployment.yaml"), out);
	}

	@Test
	void testShowOnlyMatchesFullSourcePath() {
		String out = RenderedManifest.showOnly(MANIFEST, List.of("mychart/templates/configmap.yaml"));
		assertTrue(out.contains("kind: ConfigMap"), out);
		assertFalse(out.contains("kind: Deployment"), out);
	}

	@Test
	void testShowOnlyOrdersByRequest() {
		String out = RenderedManifest.showOnly(MANIFEST,
				List.of("templates/deployment.yaml", "templates/configmap.yaml"));
		assertTrue(out.indexOf("kind: Deployment") < out.indexOf("kind: ConfigMap"), out);
	}

	@Test
	void testShowOnlyThrowsWhenTemplateMatchesNothing() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> RenderedManifest.showOnly(MANIFEST, List.of("templates/missing.yaml")));
		assertTrue(ex.getMessage().contains("templates/missing.yaml"), ex.getMessage());
	}

	@Test
	void testSkipTestsDropsHookTestDocuments() {
		String out = RenderedManifest.skipTests(MANIFEST);
		assertFalse(out.contains("kind: Pod"), out);
		assertTrue(out.contains("kind: ConfigMap"), out);
		assertTrue(out.contains("kind: Deployment"), out);
	}

	@Test
	void testGroupBySourceKeysByTemplatePath() {
		Map<String, String> grouped = RenderedManifest.groupBySource(MANIFEST);
		assertEquals(3, grouped.size());
		assertTrue(grouped.containsKey("mychart/templates/deployment.yaml"));
		assertTrue(grouped.get("mychart/templates/deployment.yaml").contains("kind: Deployment"));
	}

}
