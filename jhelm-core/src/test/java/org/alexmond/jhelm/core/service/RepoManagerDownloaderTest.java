package org.alexmond.jhelm.core.service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoManagerDownloaderTest {

	@TempDir
	Path work;

	@Test
	void routesCustomSchemeToRegisteredDownloader() throws Exception {
		byte[] tgz = chartArchive();
		AtomicReference<String> requestedUrl = new AtomicReference<>();
		ChartDownloader downloader = new ChartDownloader() {
			@Override
			public boolean supportsProtocol(String protocol) {
				return "test".equalsIgnoreCase(protocol);
			}

			@Override
			public byte[] download(String url) {
				requestedUrl.set(url);
				return tgz;
			}
		};
		RepoManager repoManager = new RepoManager();
		repoManager.setChartDownloaders(List.of(downloader));
		Path dest = Files.createDirectories(this.work.resolve("out"));

		repoManager.pullFromUrl("test://bucket/mychart-1.0.0.tgz", dest.toString(), "mychart-1.0.0.tgz");

		assertEquals("test://bucket/mychart-1.0.0.tgz", requestedUrl.get(), "downloader was consulted with the URL");
		assertTrue(Files.exists(dest.resolve("mychart-1.0.0.tgz")), "chart archive written");
		assertTrue(Files.exists(dest.resolve("mychart/Chart.yaml")), "chart archive was untarred");
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
