package org.alexmond.jhelm.rest.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/**
 * Utility to create a {@code .tgz} archive from a directory, returning the bytes in
 * memory.
 */
public final class ChartArchiveUtil {

	private ChartArchiveUtil() {
	}

	/**
	 * Tar and gzip a directory into a {@code byte[]}.
	 * @param dir the directory to archive
	 * @param entryBase the top-level directory name inside the archive
	 * @return the {@code .tgz} bytes
	 */
	public static byte[] toTgzBytes(Path dir, String entryBase) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(baos);
				TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {
			taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
			try (Stream<Path> paths = Files.walk(dir)) {
				paths.forEach((path) -> {
					try {
						if (Files.isDirectory(path)) {
							return;
						}
						Path relativePath = dir.relativize(path);
						String entryName = entryBase + "/" + relativePath;
						TarArchiveEntry entry = new TarArchiveEntry(path.toFile(), entryName);
						taos.putArchiveEntry(entry);
						Files.copy(path, taos);
						taos.closeArchiveEntry();
					}
					catch (IOException ex) {
						throw new UncheckedIOException(ex);
					}
				});
			}
			catch (UncheckedIOException ex) {
				throw ex.getCause();
			}
		}
		return baos.toByteArray();
	}

}
