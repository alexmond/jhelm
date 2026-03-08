package org.alexmond.jhelm.rest.util;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * AutoCloseable wrapper that creates a temporary directory under a configurable base path
 * and recursively deletes it on {@link #close()}.
 */
public class TempDir implements Closeable {

	private final Path path;

	public TempDir(Path baseDir, String prefix) throws IOException {
		this.path = Files.createTempDirectory(baseDir, prefix);
	}

	public Path path() {
		return this.path;
	}

	@Override
	public void close() throws IOException {
		if (Files.exists(this.path)) {
			Files.walkFileTree(this.path, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}

}
