package org.alexmond.jhelm.core.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartFilesTest {

	@Test
	void testGetReturnsFileContent() {
		ChartFiles files = new ChartFiles(Map.of("config.ini", "key=value"));
		assertEquals("key=value", files.Get("config.ini"));
	}

	@Test
	void testGetReturnsEmptyStringForMissingFile() {
		ChartFiles files = new ChartFiles(Map.of());
		assertEquals("", files.Get("missing.txt"));
	}

	@Test
	void testGetBytesReturnsContent() {
		ChartFiles files = new ChartFiles(Map.of("data.bin", "hello"));
		assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), files.GetBytes("data.bin"));
	}

	@Test
	void testGetBytesReturnsEmptyForMissingFile() {
		ChartFiles files = new ChartFiles(Map.of());
		assertArrayEquals(new byte[0], files.GetBytes("missing.bin"));
	}

	@Test
	void testLinesReturnsLines() {
		ChartFiles files = new ChartFiles(Map.of("data.txt", "line1\nline2\nline3"));
		assertEquals(List.of("line1", "line2", "line3"), files.Lines("data.txt"));
	}

	@Test
	void testLinesReturnsEmptyForMissingFile() {
		ChartFiles files = new ChartFiles(Map.of());
		assertEquals(List.of(), files.Lines("missing.txt"));
	}

	@Test
	void testLinesReturnsEmptyForEmptyContent() {
		ChartFiles files = new ChartFiles(Map.of("empty.txt", ""));
		assertEquals(List.of(), files.Lines("empty.txt"));
	}

	@Test
	void testGlobMatchesFiles() {
		Map<String, String> fileMap = new LinkedHashMap<>();
		fileMap.put("files/config.yaml", "cfg1");
		fileMap.put("files/secret.yaml", "sec1");
		fileMap.put("files/readme.txt", "readme");
		ChartFiles files = new ChartFiles(fileMap);

		Map<String, String> result = files.Glob("files/*.yaml");
		assertEquals(2, result.size());
		assertTrue(result.containsKey("files/config.yaml"));
		assertTrue(result.containsKey("files/secret.yaml"));
	}

	@Test
	void testGlobReturnsEmptyForNoMatch() {
		ChartFiles files = new ChartFiles(Map.of("data.txt", "content"));
		assertTrue(files.Glob("*.yaml").isEmpty());
	}

	@Test
	void testAsSecretsBase64EncodesContent() {
		ChartFiles files = new ChartFiles(Map.of("password.txt", "s3cret"));
		Map<String, String> secrets = files.AsSecrets();
		String expected = Base64.getEncoder().encodeToString("s3cret".getBytes(StandardCharsets.UTF_8));
		assertEquals(expected, secrets.get("password.txt"));
	}

	@Test
	void testAsConfigReturnsCopy() {
		Map<String, String> original = new LinkedHashMap<>();
		original.put("app.conf", "port=8080");
		ChartFiles files = new ChartFiles(original);
		Map<String, String> config = files.AsConfig();
		assertEquals("port=8080", config.get("app.conf"));
		// Verify it's a copy
		config.put("extra", "value");
		assertEquals("", files.Get("extra"));
	}

	@Test
	void testNullFilesMapHandled() {
		ChartFiles files = new ChartFiles(null);
		assertEquals("", files.Get("anything"));
		assertTrue(files.Glob("*").isEmpty());
		assertTrue(files.AsSecrets().isEmpty());
		assertTrue(files.AsConfig().isEmpty());
	}

	@Test
	void testToStringReturnsEmpty() {
		ChartFiles files = new ChartFiles(Map.of("a", "b"));
		assertEquals("", files.toString());
	}

}
