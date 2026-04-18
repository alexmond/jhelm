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

	/**
	 * Resolves a user-supplied name against this temp directory and verifies the result
	 * stays within the sandbox. Prevents path traversal via names like {@code ../../etc}.
	 * @param name the name to resolve (e.g. chart name)
	 * @return the resolved path guaranteed to be within this temp directory
	 * @throws IllegalArgumentException if the resolved path escapes the sandbox
	 */
	public Path sandboxedResolve(String name) {
		Path resolved = this.path.resolve(name).normalize();
		if (!resolved.startsWith(this.path)) {
			throw new IllegalArgumentException("Path traversal detected: " + name);
		}
		return resolved;
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
