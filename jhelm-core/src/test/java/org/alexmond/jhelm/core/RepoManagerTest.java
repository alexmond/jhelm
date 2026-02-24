package org.alexmond.jhelm.core;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.stubbing.Answer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepoManagerTest {

	@TempDir
	Path tempDir;

	@Test
	void testGetChartVersionsFromRealRepo() throws IOException {
		RepoManager repoManager = new RepoManager();
		repoManager.setInsecureSkipTlsVerify(true);

		// We use a real repo for integration-like test
		repoManager.addRepo("bitnami-test", "https://charts.bitnami.com/bitnami");

		List<RepoManager.ChartVersion> versions = repoManager.getChartVersions("bitnami-test", "nginx");

		assertNotNull(versions);
		assertFalse(versions.isEmpty());

		RepoManager.ChartVersion latest = versions.get(0);
		assertEquals("bitnami-test/nginx", latest.getName());
		assertNotNull(latest.getChartVersion());
		assertNotNull(latest.getAppVersion());
		assertNotNull(latest.getDescription());

		// Cleanup repo if it was added to real config (RepoManager currently uses fixed
		// paths in home dir)
		// Note: RepoManager constructor uses hardcoded paths, which is not ideal for unit
		// tests.
		// For now, we'll just verify the logic.
	}

	@Test
	void testRepoNotFound() {
		RepoManager repoManager = new RepoManager();
		assertThrows(IOException.class, () -> repoManager.getChartVersions("non-existent", "nginx"));
	}

	@Test
	void testComputeFileSha256() throws IOException {
		RepoManager repoManager = new RepoManager();
		File file = tempDir.resolve("test.bin").toFile();
		byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(content);
		}
		String actual = repoManager.computeFileSha256(file);
		assertNotNull(actual);
		assertEquals(64, actual.length());
		assertTrue(actual.matches("[0-9a-f]{64}"));
	}

	@Test
	void testComputeFileSha256KnownValue() throws IOException {
		RepoManager repoManager = new RepoManager();
		File file = tempDir.resolve("known.bin").toFile();
		// Empty file has a known SHA-256
		file.createNewFile();
		String actual = repoManager.computeFileSha256(file);
		// SHA-256 of empty input
		assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", actual);
	}

	@Test
	void testGetChartCacheFileStripsPrefix() throws IOException {
		RepoManager repoManager = new RepoManager();
		File withPrefix = repoManager.getChartCacheFile("sha256:abcdef1234");
		File withoutPrefix = repoManager.getChartCacheFile("abcdef1234");
		assertEquals(withPrefix.getName(), withoutPrefix.getName());
		assertEquals("abcdef1234.tgz", withPrefix.getName());
	}

	@Test
	void testGetChartCacheDirIsSubdirOfCacheDir() {
		RepoManager repoManager = new RepoManager();
		File chartCacheDir = repoManager.getChartCacheDir();
		assertNotNull(chartCacheDir);
		assertEquals("charts", chartCacheDir.getName());
		assertTrue(chartCacheDir.exists());
	}

	@Test
	void testComputeBytesSha256KnownValue() throws IOException {
		RepoManager repoManager = new RepoManager();
		// SHA-256 of empty byte array
		String actual = repoManager.computeBytesSha256(new byte[0]);
		assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", actual);
	}

	@Test
	void testComputeBytesSha256MatchesFileMethod() throws IOException {
		RepoManager repoManager = new RepoManager();
		byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
		File file = tempDir.resolve("match.bin").toFile();
		Files.write(file.toPath(), content);
		assertEquals(repoManager.computeBytesSha256(content), repoManager.computeFileSha256(file));
	}

	@Test
	void testVerifyBlobDigestMatch() throws IOException {
		RepoManager repoManager = new RepoManager();
		byte[] content = "chart content".getBytes(StandardCharsets.UTF_8);
		File file = tempDir.resolve("chart.tgz").toFile();
		Files.write(file.toPath(), content);
		String digest = "sha256:" + repoManager.computeFileSha256(file);
		// Should not throw
		repoManager.verifyBlobDigest(file, digest);
	}

	@Test
	void testVerifyBlobDigestMismatch() throws IOException {
		RepoManager repoManager = new RepoManager();
		File file = tempDir.resolve("tampered.tgz").toFile();
		Files.write(file.toPath(), "actual content".getBytes(StandardCharsets.UTF_8));
		assertThrows(IOException.class, () -> repoManager.verifyBlobDigest(file,
				"sha256:0000000000000000000000000000000000000000000000000000000000000000"));
	}

	@Test
	void testVerifyBlobDigestSkipsNonSha256() throws IOException {
		RepoManager repoManager = new RepoManager();
		File file = tempDir.resolve("any.tgz").toFile();
		Files.write(file.toPath(), "data".getBytes(StandardCharsets.UTF_8));
		// Should not throw for null or non-sha256 digest
		repoManager.verifyBlobDigest(file, null);
		repoManager.verifyBlobDigest(file, "md5:abc123");
	}

	@Test
	void testIsManifestIndexDetectsIndex() throws Exception {
		JsonMapper mapper = JsonMapper.builder().build();
		RepoManager repoManager = new RepoManager();

		JsonNode index = mapper.readTree("""
				{"mediaType":"application/vnd.oci.image.index.v1+json","manifests":[{"digest":"sha256:abc"}]}
				""");
		assertTrue(repoManager.isManifestIndex(index));

		JsonNode singleManifest = mapper.readTree("""
				{"mediaType":"application/vnd.oci.image.manifest.v1+json","layers":[]}
				""");
		assertFalse(repoManager.isManifestIndex(singleManifest));
	}

	@Test
	void testResolveDigestFromIndexPrefersNoPlatform() throws Exception {
		JsonMapper mapper = JsonMapper.builder().build();
		RepoManager repoManager = new RepoManager();

		JsonNode index = mapper.readTree("""
				{"manifests":[
				  {"digest":"sha256:amd64","platform":{"os":"linux","architecture":"amd64"}},
				  {"digest":"sha256:noplat"}
				]}
				""");
		assertEquals("sha256:noplat", repoManager.resolveDigestFromIndex(index));
	}

	@Test
	void testResolveDigestFromIndexPrefersLinuxAmd64() throws Exception {
		JsonMapper mapper = JsonMapper.builder().build();
		RepoManager repoManager = new RepoManager();

		JsonNode index = mapper.readTree("""
				{"manifests":[
				  {"digest":"sha256:arm64","platform":{"os":"linux","architecture":"arm64"}},
				  {"digest":"sha256:amd64","platform":{"os":"linux","architecture":"amd64"}}
				]}
				""");
		assertEquals("sha256:amd64", repoManager.resolveDigestFromIndex(index));
	}

	// ── Config file operations (use temp configPath constructor) ─────────────

	@Test
	void testLoadConfigReturnsDefaultWhenFileNotExists() throws IOException {
		RepoManager rm = new RepoManager(tempDir.resolve("nonexistent.yaml").toString());
		RepositoryConfig config = rm.loadConfig();
		assertNotNull(config);
		assertNotNull(config.getRepositories());
		assertTrue(config.getRepositories().isEmpty());
	}

	@Test
	void testSaveAndLoadConfigRoundTrip() throws IOException {
		RepoManager rm = new RepoManager(tempDir.resolve("repos.yaml").toString());
		RepositoryConfig config = RepositoryConfig.builder()
			.apiVersion("v1")
			.generated(OffsetDateTime.now().toString())
			.repositories(new ArrayList<>(List
				.of(RepositoryConfig.Repository.builder().name("myrepo").url("https://charts.example.com").build())))
			.build();
		rm.saveConfig(config);
		RepositoryConfig loaded = rm.loadConfig();
		assertEquals(1, loaded.getRepositories().size());
		assertEquals("myrepo", loaded.getRepositories().get(0).getName());
		assertEquals("https://charts.example.com", loaded.getRepositories().get(0).getUrl());
	}

	@Test
	void testRemoveRepo() throws IOException {
		RepoManager rm = new RepoManager(tempDir.resolve("repos.yaml").toString());
		RepositoryConfig config = RepositoryConfig.builder()
			.repositories(new ArrayList<>(
					List.of(RepositoryConfig.Repository.builder().name("todelete").url("https://x.com").build(),
							RepositoryConfig.Repository.builder().name("keep").url("https://y.com").build())))
			.build();
		rm.saveConfig(config);
		rm.removeRepo("todelete");
		RepositoryConfig loaded = rm.loadConfig();
		assertEquals(1, loaded.getRepositories().size());
		assertEquals("keep", loaded.getRepositories().get(0).getName());
	}

	@Test
	void testGetRepoUrlFound() throws IOException {
		RepoManager rm = new RepoManager(tempDir.resolve("repos.yaml").toString());
		RepositoryConfig config = RepositoryConfig.builder()
			.repositories(new ArrayList<>(List
				.of(RepositoryConfig.Repository.builder().name("r1").url("https://charts.example.com").build())))
			.build();
		rm.saveConfig(config);
		assertEquals("https://charts.example.com", rm.getRepoUrl("r1"));
	}

	@Test
	void testGetRepoUrlNotFound() throws IOException {
		RepoManager rm = new RepoManager(tempDir.resolve("repos.yaml").toString());
		rm.saveConfig(
				RepositoryConfig.builder().repositories(new ArrayList<>()).generated("now").apiVersion("v1").build());
		assertNull(rm.getRepoUrl("unknown"));
	}

	// ── Package-private logic methods ────────────────────────────────────────

	@Test
	void testParseOciUrlWithTag() throws IOException {
		RepoManager rm = new RepoManager();
		String[] parts = rm.parseOciUrl("oci://my.registry.io/charts/mychart:1.2.3");
		assertEquals("my.registry.io", parts[0]);
		assertEquals("charts/mychart", parts[1]);
		assertEquals("1.2.3", parts[2]);
	}

	@Test
	void testParseOciUrlDefaultsToLatest() throws IOException {
		RepoManager rm = new RepoManager();
		String[] parts = rm.parseOciUrl("oci://my.registry.io/charts/mychart");
		assertEquals("my.registry.io", parts[0]);
		assertEquals("charts/mychart", parts[1]);
		assertEquals("latest", parts[2]);
	}

	@Test
	void testParseOciUrlInvalid() {
		RepoManager rm = new RepoManager();
		assertThrows(IOException.class, () -> rm.parseOciUrl("oci://nopath"));
	}

	@ParameterizedTest
	@CsvSource({ "'', 'https://charts.example.com/mychart-1.0.0.tgz'",
			"'relative.tgz', 'https://charts.example.com/relative.tgz'",
			"'https://cdn.example.com/mychart-1.0.0.tgz', 'https://cdn.example.com/mychart-1.0.0.tgz'" })
	void testResolveChartUrl(String indexUrl, String expected) {
		RepoManager rm = new RepoManager();
		String nullableIndex = indexUrl.isEmpty() ? null : indexUrl;
		assertEquals(expected, rm.resolveChartUrl(nullableIndex, "https://charts.example.com", "mychart", "1.0.0"));
	}

	@Test
	void testLookupChartInIndexFound() throws IOException {
		RepoManager rm = new RepoManager();
		String yaml = """
				apiVersion: v1
				entries:
				  mychart:
				  - version: "1.2.3"
				    digest: "sha256:abc123"
				    urls:
				    - https://charts.example.com/mychart-1.2.3.tgz
				""";
		File indexFile = tempDir.resolve("index.yaml").toFile();
		Files.writeString(indexFile.toPath(), yaml);
		String[] result = rm.lookupChartInIndex(indexFile, "mychart", "1.2.3");
		assertNotNull(result);
		assertEquals("https://charts.example.com/mychart-1.2.3.tgz", result[0]);
		assertEquals("sha256:abc123", result[1]);
	}

	@Test
	void testLookupChartInIndexVersionNotFound() throws IOException {
		RepoManager rm = new RepoManager();
		String yaml = """
				apiVersion: v1
				entries:
				  mychart:
				  - version: "1.0.0"
				    urls:
				    - https://charts.example.com/mychart-1.0.0.tgz
				""";
		File indexFile = tempDir.resolve("index.yaml").toFile();
		Files.writeString(indexFile.toPath(), yaml);
		assertNull(rm.lookupChartInIndex(indexFile, "mychart", "9.9.9"));
	}

	@Test
	void testLookupChartInIndexFileNotExists() throws IOException {
		RepoManager rm = new RepoManager();
		assertNull(rm.lookupChartInIndex(tempDir.resolve("missing.yaml").toFile(), "mychart", "1.0.0"));
	}

	// ── Mocked HTTP operations ───────────────────────────────────────────────

	@Test
	void testClose() {
		new RepoManager().close();
	}

	@Test
	void testUpdateRepo() throws IOException {
		RepoManager rm = new RepoManager(tempDir.resolve("repos.yaml").toString());
		CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
		rm.setHttpClientForTest(mockClient);
		rm.saveConfig(RepositoryConfig.builder()
			.repositories(new ArrayList<>(List
				.of(RepositoryConfig.Repository.builder().name("testrepo").url("https://charts.example.com").build())))
			.build());
		byte[] indexContent = "apiVersion: v1\nentries: {}\n".getBytes(StandardCharsets.UTF_8);
		when(mockClient.execute(isA(HttpGet.class), any(HttpClientResponseHandler.class)))
			.thenAnswer(httpAnswer(200, indexContent));
		rm.updateRepo("testrepo");
	}

	@Test
	void testPullFromUrl() throws Exception {
		RepoManager rm = new RepoManager();
		CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
		rm.setHttpClientForTest(mockClient);
		byte[] tgz = createMinimalTgz();
		when(mockClient.execute(isA(HttpGet.class), any(HttpClientResponseHandler.class)))
			.thenAnswer(httpAnswer(200, tgz));
		rm.pullFromUrl("https://charts.example.com/mychart-1.0.0.tgz", tempDir.toString(), "mychart-1.0.0.tgz");
		assertTrue(tempDir.resolve("mychart-1.0.0.tgz").toFile().exists());
	}

	@Test
	void testPullOci() throws Exception {
		RepoManager rm = new RepoManager();
		CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
		rm.setHttpClientForTest(mockClient);
		byte[] tgz = createMinimalTgz();
		String digest = "sha256:" + sha256Hex(tgz);
		String manifest = """
				{"schemaVersion":2,"layers":[
				  {"mediaType":"application/vnd.cncf.helm.chart.content.v1.tar+gzip",
				   "digest":"%s","size":%d}
				]}""".formatted(digest, tgz.length);
		byte[] tokenJson = "{\"token\":\"mytoken\"}".getBytes(StandardCharsets.UTF_8);
		when(mockClient.execute(isA(HttpGet.class), any(HttpClientResponseHandler.class)))
			.thenAnswer(httpAnswer(200, tokenJson))
			.thenAnswer(httpAnswer(200, manifest.getBytes(StandardCharsets.UTF_8)))
			.thenAnswer(httpAnswer(200, tgz));
		rm.pullOci("oci://test.registry.io/myorg/mychart:1.0.0", tempDir.toString(), "mychart-1.0.0.tgz");
		assertTrue(tempDir.resolve("mychart-1.0.0.tgz").toFile().exists());
	}

	@Test
	void testPushOci() throws Exception {
		RepoManager rm = new RepoManager();
		CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
		rm.setHttpClientForTest(mockClient);
		byte[] tgz = createMinimalTgz();
		File chartFile = tempDir.resolve("mychart-1.0.0.tgz").toFile();
		Files.write(chartFile.toPath(), tgz);
		byte[] tokenJson = "{\"token\":\"mytoken\"}".getBytes(StandardCharsets.UTF_8);
		when(mockClient.execute(isA(HttpGet.class), any(HttpClientResponseHandler.class)))
			.thenAnswer(httpAnswer(200, tokenJson));
		// First HEAD (chart blob) → 404 not found; second HEAD (config blob) → 200 found
		when(mockClient.execute(isA(HttpHead.class), any(HttpClientResponseHandler.class)))
			.thenAnswer(httpAnswer(404, null))
			.thenAnswer(httpAnswer(200, null));
		// POST initiate upload → 202 + Location
		when(mockClient.execute(isA(HttpPost.class), any(HttpClientResponseHandler.class)))
			.thenAnswer(httpPostAnswer(202, "https://test.registry.io/v2/upload?id=1"));
		// PUT upload + manifest → 201
		when(mockClient.execute(isA(HttpPut.class), any(HttpClientResponseHandler.class)))
			.thenAnswer(httpAnswer(201, null));
		rm.pushOci(chartFile.getAbsolutePath(), "oci://test.registry.io/myorg/mychart:1.0.0");
	}

	@Test
	void testUntar() throws Exception {
		byte[] tgz = createMinimalTgz();
		File tgzFile = tempDir.resolve("minimal-1.0.0.tgz").toFile();
		Files.write(tgzFile.toPath(), tgz);
		File destDir = tempDir.resolve("out").toFile();
		destDir.mkdirs();
		new RepoManager().untar(tgzFile, destDir);
		assertTrue(new File(destDir, "minimal/Chart.yaml").exists());
	}

	// ── Test helpers ─────────────────────────────────────────────────────────

	/**
	 * Builds a minimal chart .tgz from the real test resource at
	 * src/test/resources/test-charts/minimal/.
	 */
	private static byte[] createMinimalTgz() throws Exception {
		java.net.URL resource = RepoManagerTest.class.getResource("/test-charts/minimal");
		File chartDir = new File(resource.toURI());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(baos);
				TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {
			addDirToTar(taos, chartDir, "minimal");
		}
		return baos.toByteArray();
	}

	private static void addDirToTar(TarArchiveOutputStream taos, File dir, String base) throws IOException {
		for (File file : dir.listFiles()) {
			String entryName = base + "/" + file.getName();
			if (file.isDirectory()) {
				addDirToTar(taos, file, entryName);
			}
			else {
				byte[] content = Files.readAllBytes(file.toPath());
				TarArchiveEntry entry = new TarArchiveEntry(entryName);
				entry.setSize(content.length);
				taos.putArchiveEntry(entry);
				taos.write(content);
				taos.closeArchiveEntry();
			}
		}
	}

	private static String sha256Hex(byte[] bytes) throws Exception {
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
		return HexFormat.of().formatHex(hash);
	}

	private static Answer<Object> httpAnswer(int code, byte[] body) {
		return (inv) -> {
			HttpClientResponseHandler<Object> handler = inv.getArgument(1);
			ClassicHttpResponse resp = mock(ClassicHttpResponse.class);
			when(resp.getCode()).thenReturn(code);
			if (body != null) {
				HttpEntity entity = mock(HttpEntity.class);
				when(resp.getEntity()).thenReturn(entity);
				when(entity.getContent()).thenReturn(new ByteArrayInputStream(body));
			}
			return handler.handleResponse(resp);
		};
	}

	private static Answer<Object> httpPostAnswer(int code, String locationUrl) {
		return (inv) -> {
			HttpClientResponseHandler<Object> handler = inv.getArgument(1);
			ClassicHttpResponse resp = mock(ClassicHttpResponse.class);
			when(resp.getCode()).thenReturn(code);
			Header locationHeader = mock(Header.class);
			when(locationHeader.getValue()).thenReturn(locationUrl);
			when(resp.getFirstHeader("Location")).thenReturn(locationHeader);
			return handler.handleResponse(resp);
		};
	}

}
