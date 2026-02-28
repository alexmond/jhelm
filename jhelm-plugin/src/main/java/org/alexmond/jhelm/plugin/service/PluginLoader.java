package org.alexmond.jhelm.plugin.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.plugin.exception.PluginLoadException;
import org.alexmond.jhelm.plugin.exception.PluginValidationException;
import org.alexmond.jhelm.plugin.model.PluginManifest;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Loads plugins from {@code .jhp} archive files (tar.gz format). Each archive must
 * contain a {@code plugin.yaml} manifest and a WASM binary.
 */
@Slf4j
public class PluginLoader {

	private final YAMLMapper yamlMapper = YAMLMapper.builder().build();

	/**
	 * Load a plugin manifest and WASM binary from an archive.
	 * @param archiveFile the {@code .jhp} file
	 * @return the loaded result
	 * @throws PluginLoadException if the archive cannot be read
	 * @throws PluginValidationException if the manifest is missing or invalid
	 */
	public LoadResult load(File archiveFile) throws PluginLoadException, PluginValidationException {
		PluginManifest manifest = null;
		byte[] wasmBytes = null;

		try (InputStream fis = new FileInputStream(archiveFile);
				GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
				TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {
			TarArchiveEntry entry;
			while ((entry = tais.getNextEntry()) != null) {
				String name = Path.of(entry.getName()).getFileName().toString();
				if ("plugin.yaml".equals(name) || "plugin.yml".equals(name)) {
					manifest = yamlMapper.readValue(readEntry(tais, entry), PluginManifest.class);
				}
				else if (name.endsWith(".wasm")) {
					wasmBytes = readEntry(tais, entry);
				}
			}
		}
		catch (IOException ex) {
			throw new PluginLoadException("Failed to read plugin archive: " + archiveFile.getName(), ex);
		}

		if (manifest == null) {
			throw new PluginValidationException("Plugin archive missing plugin.yaml: " + archiveFile.getName());
		}
		if (manifest.getName() == null || manifest.getName().isBlank()) {
			throw new PluginValidationException("Plugin manifest missing 'name' field");
		}
		if (manifest.getType() == null) {
			throw new PluginValidationException("Plugin manifest missing 'type' field");
		}

		String entrypoint = (manifest.getWasm() != null && manifest.getWasm().getEntrypoint() != null)
				? manifest.getWasm().getEntrypoint() : "plugin.wasm";

		if (wasmBytes == null) {
			throw new PluginValidationException(
					"Plugin archive missing WASM binary '" + entrypoint + "': " + archiveFile.getName());
		}

		if (log.isInfoEnabled()) {
			log.info("Loaded plugin manifest: {} (type: {})", manifest.getName(), manifest.getType());
		}
		return new LoadResult(manifest, wasmBytes);
	}

	private byte[] readEntry(TarArchiveInputStream tais, TarArchiveEntry entry) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream((int) entry.getSize());
		byte[] buffer = new byte[8192];
		int read;
		while ((read = tais.read(buffer)) != -1) {
			bos.write(buffer, 0, read);
		}
		return bos.toByteArray();
	}

	/**
	 * Result of loading a plugin archive.
	 */
	public record LoadResult(PluginManifest manifest, byte[] wasmBytes) {
	}

}
