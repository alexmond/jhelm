package org.alexmond.jhelm.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

}
