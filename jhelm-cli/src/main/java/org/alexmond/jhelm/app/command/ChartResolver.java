package org.alexmond.jhelm.app.command;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.action.VerifyAction;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.RepoManager;
import org.springframework.stereotype.Component;

/**
 * Resolves a chart source given on the command line — either an unpacked chart directory
 * or a packaged {@code .tgz} archive — into a loaded {@link Chart}, optionally verifying
 * the archive's PGP provenance first.
 *
 * <p>
 * When {@code verify} is requested the source must be a {@code .tgz} (a directory has no
 * {@code .prov} provenance file); verification runs before the archive is extracted, so a
 * chart that fails verification is never loaded or installed.
 */
@Slf4j
@Component
public class ChartResolver {

	private final ChartLoader chartLoader;

	private final RepoManager repoManager;

	private final VerifyAction verifyAction;

	/**
	 * Creates the resolver.
	 * @param chartLoader loads a chart from an unpacked directory
	 * @param repoManager provides the archive-extraction ({@code untar}) utility
	 * @param verifyAction verifies a packaged chart's PGP provenance
	 */
	public ChartResolver(ChartLoader chartLoader, RepoManager repoManager, VerifyAction verifyAction) {
		this.chartLoader = chartLoader;
		this.repoManager = repoManager;
		this.verifyAction = verifyAction;
	}

	/**
	 * Resolves {@code chartPath} to a loaded chart.
	 * @param chartPath a chart directory or a packaged {@code .tgz} archive
	 * @param verify whether to verify the archive's provenance before loading
	 * @param keyring path to the PGP public keyring, or {@code null} for the default
	 * @return the loaded chart
	 * @throws IOException if the archive cannot be extracted
	 * @throws IllegalArgumentException if the path is missing, or {@code verify} is set
	 * for a directory
	 */
	public Chart resolve(String chartPath, boolean verify, String keyring) throws IOException {
		File source = new File(chartPath);
		if (!source.exists()) {
			throw new IllegalArgumentException("Chart path not found: " + chartPath);
		}
		if (source.isDirectory()) {
			if (verify) {
				throw new IllegalArgumentException(
						"--verify requires a packaged .tgz chart; a chart directory has no provenance file");
			}
			return chartLoader.load(source);
		}
		if (verify) {
			// Aborts with a SignatureException before extraction if verification fails.
			verifyAction.verify(chartPath, (keyring != null) ? keyring : defaultKeyringPath());
		}
		Path workDir = Files.createTempDirectory("jhelm-chart-");
		try {
			repoManager.untar(source, workDir.toFile());
			Path chartDir = ChartLoader.findChartDir(workDir);
			// load() reads the whole chart into memory, so the temp dir can be removed
			// after.
			return chartLoader.load(chartDir.toFile());
		}
		finally {
			deleteRecursively(workDir);
		}
	}

	private static String defaultKeyringPath() {
		return System.getProperty("user.home") + "/.gnupg/pubring.gpg";
	}

	private static void deleteRecursively(Path dir) {
		try (var paths = Files.walk(dir)) {
			paths.sorted(Comparator.reverseOrder()).forEach((p) -> {
				try {
					Files.deleteIfExists(p);
				}
				catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
			});
		}
		catch (IOException | UncheckedIOException ex) {
			if (log.isWarnEnabled()) {
				log.warn("Failed to clean up temp chart dir {}: {}", dir, ex.getMessage());
			}
		}
	}

}
