package org.alexmond.jhelm.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValuesLoaderTest {

	@TempDir
	Path tempDir;

	@Test
	void testSingleDocument() throws IOException {
		File f = writeValues("""
				replicaCount: 2
				image:
				  repository: nginx
				  tag: latest
				""");
		Map<String, Object> result = ValuesLoader.load(f);
		assertEquals(2, result.get("replicaCount"));
		Map<?, ?> image = (Map<?, ?>) result.get("image");
		assertEquals("nginx", image.get("repository"));
		assertEquals("latest", image.get("tag"));
	}

	@Test
	void testMultiDocumentNonOverlapping() throws IOException {
		File f = writeValues("""
				key1: value1
				---
				key2: value2
				""");
		Map<String, Object> result = ValuesLoader.load(f);
		assertEquals("value1", result.get("key1"));
		assertEquals("value2", result.get("key2"));
	}

	@Test
	void testMultiDocumentLaterOverridesScalar() throws IOException {
		File f = writeValues("""
				replicas: 1
				name: original
				---
				replicas: 3
				""");
		Map<String, Object> result = ValuesLoader.load(f);
		assertEquals(3, result.get("replicas"));
		assertEquals("original", result.get("name"));
	}

	@Test
	void testMultiDocumentDeepMergeNestedMaps() throws IOException {
		File f = writeValues("""
				db:
				  host: localhost
				  port: 5432
				---
				db:
				  port: 5433
				  name: mydb
				""");
		Map<String, Object> result = ValuesLoader.load(f);
		Map<?, ?> db = (Map<?, ?>) result.get("db");
		assertEquals("localhost", db.get("host"));
		assertEquals(5433, db.get("port"));
		assertEquals("mydb", db.get("name"));
	}

	@Test
	void testEmptyDocumentSkipped() throws IOException {
		File f = writeValues("""
				key1: value1
				---
				---
				key2: value2
				""");
		Map<String, Object> result = ValuesLoader.load(f);
		assertEquals("value1", result.get("key1"));
		assertEquals("value2", result.get("key2"));
		assertEquals(2, result.size());
	}

	@Test
	void testAllEmptyDocuments() throws IOException {
		File f = writeValues("---\n---\n");
		Map<String, Object> result = ValuesLoader.load(f);
		assertTrue(result.isEmpty());
	}

	@Test
	void testEmptyFile() throws IOException {
		File f = writeValues("");
		Map<String, Object> result = ValuesLoader.load(f);
		assertTrue(result.isEmpty());
	}

	@Test
	void testDeepMergeDirectly() {
		Map<String, Object> base = new HashMap<>();
		Map<String, Object> nested = new HashMap<>();
		nested.put("a", 1);
		base.put("nested", nested);
		base.put("scalar", "original");

		Map<String, Object> override = new HashMap<>();
		Map<String, Object> nestedOverride = new HashMap<>();
		nestedOverride.put("b", 2);
		override.put("nested", nestedOverride);
		override.put("scalar", "updated");

		ValuesLoader.deepMerge(base, override);

		assertEquals("updated", base.get("scalar"));
		Map<?, ?> mergedNested = (Map<?, ?>) base.get("nested");
		assertEquals(1, mergedNested.get("a"));
		assertEquals(2, mergedNested.get("b"));
	}

	@Test
	void testYamlAnchorAliasResolution() throws IOException {
		File f = writeValues("""
				portName: &portName http
				livenessProbe:
				  httpGet:
				    port: *portName
				""");
		Map<String, Object> result = ValuesLoader.load(f);
		assertEquals("http", result.get("portName"));
		Map<?, ?> probe = (Map<?, ?>) result.get("livenessProbe");
		Map<?, ?> httpGet = (Map<?, ?>) probe.get("httpGet");
		assertEquals("http", httpGet.get("port"));
	}

	private File writeValues(String content) throws IOException {
		Path file = tempDir.resolve("values.yaml");
		Files.writeString(file, content);
		return file.toFile();
	}

}
