package org.alexmond.jhelm.core.util;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

	@ParameterizedTest(name = "YAML null literal \"{0}\" is parsed as null")
	@ValueSource(strings = { "~", "null", "Null", "NULL", "" })
	void testNullLiteralParsedAsNull(String literal) throws IOException {
		File f = writeValues("key: " + literal + "\nother: value\n");
		Map<String, Object> result = ValuesLoader.load(f);
		assertTrue(result.containsKey("key"), "key should exist");
		assertNull(result.get("key"), "'" + literal + "' should be parsed as null");
		assertEquals("value", result.get("other"));
	}

	// --- isUrl ---

	@Test
	void testIsUrlDetectsHttp() {
		assertTrue(ValuesLoader.isUrl("http://example.com/values.yaml"));
		assertTrue(ValuesLoader.isUrl("https://example.com/values.yaml"));
		assertTrue(ValuesLoader.isUrl("HTTP://EXAMPLE.COM/values.yaml"));
		assertTrue(ValuesLoader.isUrl("HTTPS://EXAMPLE.COM/values.yaml"));
	}

	@Test
	void testIsUrlRejectsFilePaths() {
		assertFalse(ValuesLoader.isUrl("/tmp/values.yaml"));
		assertFalse(ValuesLoader.isUrl("./values.yaml"));
		assertFalse(ValuesLoader.isUrl("values.yaml"));
	}

	// --- loadFromUrl ---

	@Test
	void testLoadFromUrl() throws Exception {
		String yaml = "replicas: 3\nimage: nginx\n";
		HttpServer server = startServer(200, yaml);
		try {
			int port = server.getAddress().getPort();
			Map<String, Object> result = ValuesLoader.loadFromUrl("http://localhost:" + port + "/values.yaml");
			assertEquals(3, result.get("replicas"));
			assertEquals("nginx", result.get("image"));
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	void testLoadFromUrlNestedValues() throws Exception {
		String yaml = """
				db:
				  host: remotehost
				  port: 5432
				""";
		HttpServer server = startServer(200, yaml);
		try {
			int port = server.getAddress().getPort();
			Map<String, Object> result = ValuesLoader.loadFromUrl("http://localhost:" + port + "/values.yaml");
			Map<?, ?> db = (Map<?, ?>) result.get("db");
			assertEquals("remotehost", db.get("host"));
			assertEquals(5432, db.get("port"));
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	void testLoadFromUrlHttpError() throws Exception {
		HttpServer server = startServer(404, "not found");
		try {
			int port = server.getAddress().getPort();
			String url = "http://localhost:" + port + "/missing.yaml";
			IOException ex = assertThrows(IOException.class, () -> ValuesLoader.loadFromUrl(url));
			assertTrue(ex.getMessage().contains("HTTP 404"));
		}
		finally {
			server.stop(0);
		}
	}

	private HttpServer startServer(int statusCode, String body) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/", (exchange) -> {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(statusCode, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		server.start();
		return server;
	}

	private File writeValues(String content) throws IOException {
		Path file = tempDir.resolve("values.yaml");
		Files.writeString(file, content);
		return file.toFile();
	}

}
