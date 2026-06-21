package org.alexmond.jhelm.core.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.gotmpl4j.GoTemplate;
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
		// AsSecrets returns a YAML string keyed by base file name with base64 values,
		// matching Helm's .AsSecrets.
		ChartFiles files = new ChartFiles(Map.of("creds/password.txt", "s3cret"));
		String secrets = files.AsSecrets();
		String b64 = Base64.getEncoder().encodeToString("s3cret".getBytes(StandardCharsets.UTF_8));
		assertEquals("password.txt: " + b64, secrets);
	}

	@Test
	void testAsConfigReturnsYamlKeyedByBaseName() {
		// AsConfig returns a YAML string keyed by base file name, matching Helm's
		// .AsConfig (used as ConfigMap data via `{{ .AsConfig | indent 2 }}`).
		Map<String, String> original = new LinkedHashMap<>();
		original.put("conf/app.conf", "port=8080");
		ChartFiles files = new ChartFiles(original);
		assertEquals("app.conf: port=8080", files.AsConfig());
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

	@Test
	void testGlobThenAsConfigThroughTemplate() throws Exception {
		// The common ConfigMap pattern `{{ (.Files.Glob "scripts/*.sh").AsConfig }}`:
		// Glob returns a ChartFiles (a map), and .AsConfig must resolve as a method even
		// though ChartFiles is a map — exercises the executor's method fallback.
		ChartFiles files = new ChartFiles(Map.of("scripts/a.sh", "echo hi", "values.yaml", "x: 1"));
		GoTemplate t = new GoTemplate();
		t.parse("test", "{{ (.Files.Glob \"scripts/*.sh\").AsConfig }}");
		java.io.StringWriter w = new java.io.StringWriter();
		t.execute("test", new java.util.HashMap<>(Map.of("Files", files)), w);
		assertEquals("a.sh: echo hi", w.toString());
	}

	@Test
	void testRangeOverGlob() throws Exception {
		// A ChartFiles must remain rangeable (it is a map) so `range $p, $c :=
		// .Files.Glob`
		// works like Helm's files object.
		ChartFiles files = new ChartFiles(Map.of("scripts/a.sh", "AAA"));
		GoTemplate t = new GoTemplate();
		t.parse("test", "{{ range $p, $c := .Files.Glob \"scripts/*.sh\" }}{{ $p }}={{ $c }}{{ end }}");
		java.io.StringWriter w = new java.io.StringWriter();
		t.execute("test", new java.util.HashMap<>(Map.of("Files", files)), w);
		assertEquals("scripts/a.sh=AAA", w.toString());
	}

}
