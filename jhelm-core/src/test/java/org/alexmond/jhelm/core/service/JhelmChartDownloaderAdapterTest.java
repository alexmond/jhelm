package org.alexmond.jhelm.core.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.alexmond.jhelm.pluginapi.JhelmChartDownloader;
import org.alexmond.jhelm.pluginapi.JhelmPluginException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JhelmChartDownloaderAdapterTest {

	@TempDir
	Path work;

	@Test
	void adapterDelegatesSupportsAndDownload() throws Exception {
		JhelmChartDownloader plugin = new JhelmChartDownloader() {
			@Override
			public boolean supports(String scheme) {
				return "jtest".equals(scheme);
			}

			@Override
			public byte[] download(String url) {
				return url.getBytes(StandardCharsets.UTF_8);
			}
		};
		JhelmChartDownloaderAdapter adapter = new JhelmChartDownloaderAdapter(plugin);
		assertTrue(adapter.supportsProtocol("jtest"));
		assertEquals("jtest://x", new String(adapter.download("jtest://x"), StandardCharsets.UTF_8));
	}

	@Test
	void adapterTranslatesPluginExceptionToIoException() {
		JhelmChartDownloader failing = new JhelmChartDownloader() {
			@Override
			public boolean supports(String scheme) {
				return true;
			}

			@Override
			public byte[] download(String url) throws JhelmPluginException {
				throw new JhelmPluginException("nope");
			}
		};
		IOException ex = assertThrows(IOException.class,
				() -> new JhelmChartDownloaderAdapter(failing).download("x://y"));
		assertTrue(ex.getMessage().contains("nope"));
	}

	@Test
	void repoManagerRoutesCustomSchemeToAJavaDownloader() throws Exception {
		byte[] tgz = chartArchive();
		JhelmChartDownloader plugin = new JhelmChartDownloader() {
			@Override
			public boolean supports(String scheme) {
				return "jtest".equalsIgnoreCase(scheme);
			}

			@Override
			public byte[] download(String url) {
				return tgz;
			}
		};
		RepoManager repoManager = new RepoManager();
		repoManager.setChartDownloaders(List.of(new JhelmChartDownloaderAdapter(plugin)));
		Path dest = Files.createDirectories(this.work.resolve("out"));

		repoManager.pullFromUrl("jtest://bucket/mychart-1.0.0.tgz", dest.toString(), "mychart-1.0.0.tgz");

		assertTrue(Files.exists(dest.resolve("mychart/Chart.yaml")),
				"chart fetched via the Java downloader and untarred");
	}

	private static byte[] chartArchive() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (OutputStream gz = new GZIPOutputStream(bytes);
				TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
			byte[] content = "apiVersion: v2\nname: mychart\nversion: 1.0.0\n".getBytes(StandardCharsets.UTF_8);
			TarArchiveEntry entry = new TarArchiveEntry("mychart/Chart.yaml");
			entry.setSize(content.length);
			tar.putArchiveEntry(entry);
			tar.write(content);
			tar.closeArchiveEntry();
		}
		return bytes.toByteArray();
	}

}
