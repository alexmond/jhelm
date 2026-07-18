package org.alexmond.jhelm.app.plugin;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Refreshes a git-checked-out plugin in place (a {@code git pull}). Abstracted so the
 * installer can be unit-tested without a network or a git binary.
 */
@FunctionalInterface
public interface GitUpdater {

	/**
	 * Updates the git working tree at {@code dir} to the latest of its tracked branch.
	 * @param dir the plugin directory (a git checkout)
	 * @throws IOException if the update fails
	 * @throws InterruptedException if interrupted while waiting for git
	 */
	void update(Path dir) throws IOException, InterruptedException;

}
