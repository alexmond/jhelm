package org.alexmond.jhelm.core.util;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestDocumentsTest {

	private static long nonBlank(String[] docs) {
		return Arrays.stream(docs).filter((d) -> !d.isBlank()).count();
	}

	@Test
	void splitsOnSeparatorLines() {
		String manifest = """
				apiVersion: v1
				kind: ConfigMap
				---
				apiVersion: v1
				kind: Secret
				""";
		assertEquals(2, nonBlank(ManifestDocuments.split(manifest)));
	}

	@Test
	void doesNotSplitOnDashesInsideAComment() {
		// issue #713: a decorative comment containing --- must not start a new document
		String manifest = """
				# ---------------- Postgres ----------------
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: repro-dash
				""";
		String[] docs = ManifestDocuments.split(manifest);
		assertEquals(1, nonBlank(docs), "a comment containing --- must not split the document");
		assertTrue(Arrays.stream(docs).anyMatch((d) -> d.contains("kind: ConfigMap")));
	}

	@Test
	void doesNotSplitOnDashesInsideContent() {
		String manifest = """
				apiVersion: v1
				kind: ConfigMap
				data:
				  note: "a value with --- inside"
				""";
		assertEquals(1, nonBlank(ManifestDocuments.split(manifest)));
	}

	@Test
	void splitsOnSeparatorWithTrailingSpacesOrComment() {
		// a separator line may carry trailing whitespace or a trailing comment
		String manifest = "kind: A\n---  \nkind: B\n--- # next doc\nkind: C\n";
		assertEquals(3, nonBlank(ManifestDocuments.split(manifest)));
	}

	@Test
	void handlesCrlfSeparators() {
		String manifest = "kind: A\r\n---\r\nkind: B\r\n";
		assertEquals(2, nonBlank(ManifestDocuments.split(manifest)));
	}

}
