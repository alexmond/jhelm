package org.alexmond.jhelm.rest.util;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempDirTest {

	@TempDir
	Path baseDir;

	@Test
	void sandboxedResolveValidName() throws Exception {
		try (org.alexmond.jhelm.rest.util.TempDir tempDir = new org.alexmond.jhelm.rest.util.TempDir(this.baseDir,
				"test-")) {
			Path resolved = tempDir.sandboxedResolve("my-chart");
			assertTrue(resolved.startsWith(tempDir.path()));
			assertEquals("my-chart", resolved.getFileName().toString());
		}
	}

	@Test
	void sandboxedResolveRejectsPathTraversal() throws Exception {
		try (org.alexmond.jhelm.rest.util.TempDir tempDir = new org.alexmond.jhelm.rest.util.TempDir(this.baseDir,
				"test-")) {
			assertThrows(IllegalArgumentException.class, () -> tempDir.sandboxedResolve("../../etc/passwd"));
		}
	}

	@Test
	void sandboxedResolveRejectsDotDot() throws Exception {
		try (org.alexmond.jhelm.rest.util.TempDir tempDir = new org.alexmond.jhelm.rest.util.TempDir(this.baseDir,
				"test-")) {
			assertThrows(IllegalArgumentException.class, () -> tempDir.sandboxedResolve(".."));
		}
	}

	@Test
	void sandboxedResolveAllowsSubdirectory() throws Exception {
		try (org.alexmond.jhelm.rest.util.TempDir tempDir = new org.alexmond.jhelm.rest.util.TempDir(this.baseDir,
				"test-")) {
			Path resolved = tempDir.sandboxedResolve("charts/nginx");
			assertTrue(resolved.startsWith(tempDir.path()));
		}
	}

	@Test
	void closeDeletesTempDirectory() throws Exception {
		Path tempPath;
		try (org.alexmond.jhelm.rest.util.TempDir tempDir = new org.alexmond.jhelm.rest.util.TempDir(this.baseDir,
				"test-")) {
			tempPath = tempDir.path();
			Files.writeString(tempPath.resolve("test.txt"), "content");
			assertTrue(Files.exists(tempPath));
		}
		assertFalse(Files.exists(tempPath));
	}

}
