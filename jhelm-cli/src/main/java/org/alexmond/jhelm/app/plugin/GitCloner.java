package org.alexmond.jhelm.app.plugin;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Clones a git repository into a destination directory. Abstracted so the installer can
 * be unit-tested without a network or a git binary.
 */
@FunctionalInterface
public interface GitCloner {

	/**
	 * Clones {@code url} into {@code dest}, checking out {@code ref} when non-null.
	 * @param url the git repository URL
	 * @param ref an optional branch/tag/commit to check out, or {@code null} for the
	 * default branch
	 * @param dest the destination directory (created by the clone)
	 * @throws IOException if the clone fails
	 * @throws InterruptedException if interrupted while waiting for git
	 */
	void clone(String url, String ref, Path dest) throws IOException, InterruptedException;

}
