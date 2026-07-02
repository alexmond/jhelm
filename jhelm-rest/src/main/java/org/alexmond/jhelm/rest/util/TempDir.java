package org.alexmond.jhelm.rest.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
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

	/**
	 * Creates a fresh temporary directory under the given base path.
	 * @param baseDir the base directory in which to create the temporary directory
	 * @param prefix the prefix for the generated directory name
	 * @throws UncheckedIOException if the directory cannot be created
	 */
	public TempDir(Path baseDir, String prefix) {
		try {
			this.path = Files.createTempDirectory(baseDir, prefix);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to create temp directory under " + baseDir, ex);
		}
	}

	/**
	 * Returns the path of this temporary directory.
	 * @return the temporary directory path
	 */
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

	/**
	 * Recursively deletes this temporary directory. Narrows {@link Closeable#close()} to
	 * throw an unchecked {@link UncheckedIOException} so try-with-resources callers need
	 * no checked-exception handling.
	 * @throws UncheckedIOException if the directory cannot be deleted
	 */
	@Override
	public void close() {
		if (!Files.exists(this.path)) {
			return;
		}
		try {
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
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to delete temp directory: " + this.path, ex);
		}
	}

}
