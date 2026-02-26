package org.alexmond.jhelm.plugin.service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import org.alexmond.jhelm.plugin.exception.PluginLoadException;
import org.alexmond.jhelm.plugin.exception.PluginValidationException;
import org.alexmond.jhelm.plugin.model.PluginType;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginLoaderTest {

	private final PluginLoader loader = new PluginLoader();

	@TempDir
	File tempDir;

	@Test
	void loadValidArchive() throws Exception {
		String manifest = """
				apiVersion: v1
				name: test-plugin
				version: "1.0.0"
				type: postrenderer
				description: A test plugin
				runtime: wasm
				wasm:
				  entrypoint: plugin.wasm
				  memoryLimitPages: 128
				  timeoutSeconds: 15
				""";
		byte[] wasmBytes = new byte[] { 0x00, 0x61, 0x73, 0x6D }; // WASM magic

		File archive = createArchive("test.jhp", manifest, "plugin.wasm", wasmBytes);

		PluginLoader.LoadResult result = loader.load(archive);
		assertNotNull(result.manifest());
		assertEquals("test-plugin", result.manifest().getName());
		assertEquals(PluginType.POST_RENDERER, result.manifest().getType());
		assertEquals("1.0.0", result.manifest().getVersion());
		assertEquals(128, result.manifest().getWasm().getMemoryLimitPages());
		assertEquals(15, result.manifest().getWasm().getTimeoutSeconds());
		assertArrayEquals(wasmBytes, result.wasmBytes());
	}

	@Test
	void loadArchiveMissingManifestThrowsValidation() throws Exception {
		byte[] wasmBytes = new byte[] { 0x00, 0x61, 0x73, 0x6D };
		File archive = createArchiveWithoutManifest("no-manifest.jhp", "plugin.wasm", wasmBytes);

		assertThrows(PluginValidationException.class, () -> loader.load(archive));
	}

	@Test
	void loadArchiveMissingWasmThrowsValidation() throws Exception {
		String manifest = """
				apiVersion: v1
				name: test-plugin
				version: "1.0.0"
				type: downloader
				runtime: wasm
				""";
		File archive = createArchiveManifestOnly("no-wasm.jhp", manifest);

		assertThrows(PluginValidationException.class, () -> loader.load(archive));
	}

	@Test
	void loadNonexistentArchiveThrowsLoadException() {
		File nonexistent = new File(tempDir, "nonexistent.jhp");
		assertThrows(PluginLoadException.class, () -> loader.load(nonexistent));
	}

	private File createArchive(String name, String manifest, String wasmName, byte[] wasmBytes) throws Exception {
		File archive = new File(tempDir, name);
		try (FileOutputStream fos = new FileOutputStream(archive);
				GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
				TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {
			byte[] manifestBytes = manifest.getBytes(StandardCharsets.UTF_8);
			TarArchiveEntry manifestEntry = new TarArchiveEntry("plugin.yaml");
			manifestEntry.setSize(manifestBytes.length);
			taos.putArchiveEntry(manifestEntry);
			taos.write(manifestBytes);
			taos.closeArchiveEntry();

			TarArchiveEntry wasmEntry = new TarArchiveEntry(wasmName);
			wasmEntry.setSize(wasmBytes.length);
			taos.putArchiveEntry(wasmEntry);
			taos.write(wasmBytes);
			taos.closeArchiveEntry();

			taos.finish();
		}
		return archive;
	}

	private File createArchiveWithoutManifest(String name, String wasmName, byte[] wasmBytes) throws Exception {
		File archive = new File(tempDir, name);
		try (FileOutputStream fos = new FileOutputStream(archive);
				GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
				TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {
			TarArchiveEntry wasmEntry = new TarArchiveEntry(wasmName);
			wasmEntry.setSize(wasmBytes.length);
			taos.putArchiveEntry(wasmEntry);
			taos.write(wasmBytes);
			taos.closeArchiveEntry();

			taos.finish();
		}
		return archive;
	}

	private File createArchiveManifestOnly(String name, String manifest) throws Exception {
		File archive = new File(tempDir, name);
		try (FileOutputStream fos = new FileOutputStream(archive);
				GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
				TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {
			byte[] manifestBytes = manifest.getBytes(StandardCharsets.UTF_8);
			TarArchiveEntry manifestEntry = new TarArchiveEntry("plugin.yaml");
			manifestEntry.setSize(manifestBytes.length);
			taos.putArchiveEntry(manifestEntry);
			taos.write(manifestBytes);
			taos.closeArchiveEntry();

			taos.finish();
		}
		return archive;
	}

}
