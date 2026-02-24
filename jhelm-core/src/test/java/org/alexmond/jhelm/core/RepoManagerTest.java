package org.alexmond.jhelm.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

}
