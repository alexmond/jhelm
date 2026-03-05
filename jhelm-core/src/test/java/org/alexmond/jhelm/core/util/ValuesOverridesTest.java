package org.alexmond.jhelm.core.util;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValuesOverridesTest {

	@TempDir
	Path tempDir;

	// --- applySet ---

	@Test
	void testApplySetSimpleKey() {
		Map<String, Object> target = new HashMap<>();
		ValuesOverrides.applySet(target, "key=value");
		assertEquals("value", target.get("key"));
	}

	@Test
	void testApplySetDotNotation() {
		Map<String, Object> target = new HashMap<>();
		ValuesOverrides.applySet(target, "outer.inner=v");
		Map<?, ?> outer = (Map<?, ?>) target.get("outer");
		assertEquals("v", outer.get("inner"));
	}

	@Test
	void testApplySetDeepNesting() {
		Map<String, Object> target = new HashMap<>();
		ValuesOverrides.applySet(target, "a.b.c=deep");
		Map<?, ?> a = (Map<?, ?>) target.get("a");
		Map<?, ?> b = (Map<?, ?>) a.get("b");
		assertEquals("deep", b.get("c"));
	}

	@Test
	void testApplySetMergesIntoExistingMap() {
		Map<String, Object> target = new HashMap<>();
		ValuesOverrides.applySet(target, "db.host=localhost");
		ValuesOverrides.applySet(target, "db.port=5432");
		Map<?, ?> db = (Map<?, ?>) target.get("db");
		assertEquals("localhost", db.get("host"));
		assertEquals("5432", db.get("port"));
	}

	@Test
	void testApplySetOverwritesExistingScalar() {
		Map<String, Object> target = new HashMap<>();
		ValuesOverrides.applySet(target, "key=old");
		ValuesOverrides.applySet(target, "key=new");
		assertEquals("new", target.get("key"));
	}

	@Test
	void testApplySetIgnoresMissingEquals() {
		Map<String, Object> target = new HashMap<>();
		ValuesOverrides.applySet(target, "noequals");
		assertTrue(target.isEmpty());
	}

	@Test
	void testApplySetValueWithEquals() {
		Map<String, Object> target = new HashMap<>();
		ValuesOverrides.applySet(target, "key=a=b");
		assertEquals("a=b", target.get("key"));
	}

	// --- parse from files ---

	@Test
	void testParseFromSingleFile() throws Exception {
		Path f = writeValues("replicas: 3\nimage: nginx\n");
		Map<String, Object> result = ValuesOverrides.parse(List.of(f.toString()), null);
		assertEquals(3, result.get("replicas"));
		assertEquals("nginx", result.get("image"));
	}

	@Test
	void testParseFromMultipleFiles() throws Exception {
		Path f1 = writeFile("f1.yaml", "key1: v1\n");
		Path f2 = writeFile("f2.yaml", "key2: v2\n");
		Map<String, Object> result = ValuesOverrides.parse(List.of(f1.toString(), f2.toString()), null);
		assertEquals("v1", result.get("key1"));
		assertEquals("v2", result.get("key2"));
	}

	@Test
	void testParseFilesLaterOverridesEarlier() throws Exception {
		Path f1 = writeFile("base.yaml", "replicas: 1\nimage: nginx\n");
		Path f2 = writeFile("override.yaml", "replicas: 5\n");
		Map<String, Object> result = ValuesOverrides.parse(List.of(f1.toString(), f2.toString()), null);
		assertEquals(5, result.get("replicas"));
		assertEquals("nginx", result.get("image"));
	}

	// --- parse from --set ---

	@Test
	void testParseFromSetArgs() throws Exception {
		Map<String, Object> result = ValuesOverrides.parse(null, List.of("key=value", "other=123"));
		assertEquals("value", result.get("key"));
		assertEquals("123", result.get("other"));
	}

	// --- combined ---

	@Test
	void testSetOverridesFile() throws Exception {
		Path f = writeValues("replicas: 1\nimage: nginx\n");
		Map<String, Object> result = ValuesOverrides.parse(List.of(f.toString()), List.of("replicas=3"));
		assertEquals("3", result.get("replicas"));
		assertEquals("nginx", result.get("image"));
	}

	@Test
	void testEmptyInputs() throws Exception {
		Map<String, Object> result = ValuesOverrides.parse(List.of(), List.of());
		assertTrue(result.isEmpty());
	}

	@Test
	void testNullInputs() throws Exception {
		Map<String, Object> result = ValuesOverrides.parse(null, null);
		assertTrue(result.isEmpty());
	}

	// --- URL value files ---

	@Test
	void testUrlValueFile() throws Exception {
		String yaml = "replicas: 5\nimage: redis\n";
		HttpServer server = startServer(yaml);
		try {
			int port = server.getAddress().getPort();
			String url = "http://localhost:" + port + "/values.yaml";
			Map<String, Object> result = ValuesOverrides.parse(List.of(url), null);
			assertEquals(5, result.get("replicas"));
			assertEquals("redis", result.get("image"));
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	void testMixedLocalAndUrlFiles() throws Exception {
		Path localFile = writeFile("local.yaml", """
				replicas: 1
				image: nginx
				tag: latest
				""");
		String remoteYaml = "replicas: 3\ntag: stable\n";
		HttpServer server = startServer(remoteYaml);
		try {
			int port = server.getAddress().getPort();
			String url = "http://localhost:" + port + "/override.yaml";
			Map<String, Object> result = ValuesOverrides.parse(List.of(localFile.toString(), url), null);
			assertEquals(3, result.get("replicas"));
			assertEquals("nginx", result.get("image"));
			assertEquals("stable", result.get("tag"));
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	void testSetOverridesUrlFile() throws Exception {
		String yaml = "replicas: 5\nimage: nginx\n";
		HttpServer server = startServer(yaml);
		try {
			int port = server.getAddress().getPort();
			String url = "http://localhost:" + port + "/values.yaml";
			Map<String, Object> result = ValuesOverrides.parse(List.of(url), List.of("replicas=10"));
			assertEquals("10", result.get("replicas"));
			assertEquals("nginx", result.get("image"));
		}
		finally {
			server.stop(0);
		}
	}

	// --- helpers ---

	private HttpServer startServer(String body) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/", (exchange) -> {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		server.start();
		return server;
	}

	private Path writeValues(String content) throws IOException {
		return writeFile("values.yaml", content);
	}

	private Path writeFile(String name, String content) throws IOException {
		Path file = tempDir.resolve(name);
		Files.writeString(file, content);
		return file;
	}

}
