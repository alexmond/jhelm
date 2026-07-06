package org.alexmond.jhelm.app.command;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.action.VerifyAction;
import org.alexmond.jhelm.core.model.RepositoryConfig;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.util.ValuesProfiles;
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
	// S5443: the work directory is created via NIO createTempDirectory with owner-only
	// permissions and holds only the extracted chart (no secrets), so the shared temp
	// directory is used safely here.
	@SuppressWarnings("java:S5443")
	public Chart resolve(String chartPath, boolean verify, String keyring) throws IOException {
		return resolve(chartPath, verify, keyring, ValuesProfiles.none());
	}

	/**
	 * Resolves {@code chartPath} to a loaded chart, applying the given value profiles to
	 * its {@code values.yaml} and {@code values-<profile>.yaml} sidecars.
	 * @param chartPath a chart directory or a packaged {@code .tgz} archive
	 * @param verify whether to verify the archive's provenance before loading
	 * @param keyring path to the PGP public keyring, or {@code null} for the default
	 * @param profiles the active value profiles
	 * @return the loaded chart
	 * @throws IOException if the archive cannot be extracted
	 * @throws IllegalArgumentException if the path is missing, or {@code verify} is set
	 * for a directory
	 */
	@SuppressWarnings("java:S5443")
	public Chart resolve(String chartPath, boolean verify, String keyring, ValuesProfiles profiles) throws IOException {
		File source = new File(chartPath);
		if (!source.exists()) {
			throw new IllegalArgumentException("Chart path not found: " + chartPath);
		}
		if (source.isDirectory()) {
			if (verify) {
				throw new IllegalArgumentException(
						"--verify requires a packaged .tgz chart; a chart directory has no provenance file");
			}
			return chartLoader.load(source, profiles);
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
			return chartLoader.load(chartDir.toFile(), profiles);
		}
		finally {
			deleteRecursively(workDir);
		}
	}

	/**
	 * Resolves a chart that may be a local path <em>or</em> a repository reference,
	 * mirroring {@code helm install/upgrade [--repo URL] CHART --version V}. A local
	 * directory or {@code .tgz} is loaded directly; otherwise the chart is pulled into a
	 * temporary directory (from {@code repoUrl} when given, else from a registered
	 * repository or an {@code oci://} URL) and the downloaded archive is loaded.
	 * @param chartRef a local chart path, a {@code repo/chart} reference, or an
	 * {@code oci://} URL
	 * @param version the chart version (required for repository/{@code --repo} pulls)
	 * @param repoUrl an ad-hoc repository URL (Helm's {@code --repo}), or {@code null}
	 * @param auth the repository auth/TLS descriptor for a {@code --repo} pull, or
	 * {@code null}
	 * @param verify whether to verify the pulled archive's provenance
	 * @param keyring path to the PGP public keyring, or {@code null} for the default
	 * @param profiles the active value profiles
	 * @return the loaded chart
	 * @throws IOException if the chart cannot be pulled or loaded
	 */
	@SuppressWarnings("java:S5443")
	public Chart resolveFromRepo(String chartRef, String version, String repoUrl, RepositoryConfig.Repository auth,
			boolean verify, String keyring, ValuesProfiles profiles) throws IOException {
		File local = new File(chartRef);
		boolean fromRepo = (repoUrl != null && !repoUrl.isBlank());
		if (!fromRepo && local.exists()) {
			return resolve(chartRef, verify, keyring, profiles);
		}
		Path pullDir = Files.createTempDirectory("jhelm-pull-");
		try {
			if (fromRepo) {
				repoManager.pullFromRepoUrl(repoUrl, chartRef, version, pullDir.toString(), auth);
			}
			else {
				repoManager.pull(chartRef, version, pullDir.toString());
			}
			File archive = findArchive(pullDir, chartRef);
			return resolve(archive.getPath(), verify, keyring, profiles);
		}
		finally {
			deleteRecursively(pullDir);
		}
	}

	// Locates the single .tgz the pull wrote into the temp directory.
	private static File findArchive(Path pullDir, String chartRef) throws IOException {
		try (var paths = Files.list(pullDir)) {
			return paths.filter((p) -> p.getFileName().toString().endsWith(".tgz"))
				.findFirst()
				.map(Path::toFile)
				.orElseThrow(() -> new IOException("No chart archive was pulled for '" + chartRef + "'"));
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
