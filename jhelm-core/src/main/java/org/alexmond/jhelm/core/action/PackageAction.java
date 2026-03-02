package org.alexmond.jhelm.core.action;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.SignatureService;

/**
 * Packages a chart directory into a versioned {@code .tgz} archive and optionally
 * produces a PGP provenance ({@code .prov}) file.
 */
@Slf4j
@RequiredArgsConstructor
public class PackageAction {

	private final ChartLoader chartLoader;

	private final SignatureService signatureService;

	@Setter
	private File destination;

	/**
	 * Packages a chart directory into a {@code .tgz} archive.
	 * @param chartPath path to the chart directory
	 * @return the created archive file
	 */
	public File packageChart(String chartPath) throws Exception {
		return packageChart(chartPath, null, null);
	}

	/**
	 * Packages a chart directory and signs it using the specified keyring and key ID.
	 * @param chartPath path to the chart directory
	 * @param keyringPath path to the PGP secret keyring file
	 * @param keyId substring to match against key user IDs
	 * @param passphrase the key passphrase
	 * @return the created archive file
	 */
	public File packageChart(String chartPath, String keyringPath, String keyId, char[] passphrase) throws Exception {
		PGPSecretKey secretKey = signatureService.loadSecretKey(keyringPath, keyId);
		return packageChart(chartPath, secretKey, passphrase);
	}

	/**
	 * Packages a chart directory into a {@code .tgz} archive and optionally signs it.
	 * @param chartPath path to the chart directory
	 * @param secretKey PGP secret key for signing, or {@code null} to skip signing
	 * @param passphrase key passphrase, or {@code null} if not signing
	 * @return the created archive file
	 */
	public File packageChart(String chartPath, PGPSecretKey secretKey, char[] passphrase) throws Exception {
		Chart chart = chartLoader.load(new File(chartPath));
		String archiveName = chart.getMetadata().getName() + "-" + chart.getMetadata().getVersion() + ".tgz";

		File destDir = (destination != null) ? destination : new File(".");
		File archiveFile = new File(destDir, archiveName);

		createTgz(new File(chartPath), chart.getMetadata().getName(), archiveFile);
		if (log.isInfoEnabled()) {
			log.info("Successfully packaged chart and saved it to: {}", archiveFile.getAbsolutePath());
		}

		if (secretKey != null) {
			String provContent = signatureService.sign(archiveFile, chart.getMetadata(), secretKey, passphrase);
			File provFile = new File(destDir, archiveName + ".prov");
			Files.writeString(provFile.toPath(), provContent);
			if (log.isInfoEnabled()) {
				log.info("Successfully signed chart and saved provenance to: {}", provFile.getAbsolutePath());
			}
		}

		return archiveFile;
	}

	private void createTgz(File chartDir, String chartName, File outputFile) throws IOException {
		List<PathMatcher> ignoreMatchers = loadHelmIgnore(chartDir);
		try (OutputStream fos = Files.newOutputStream(outputFile.toPath());
				BufferedOutputStream bos = new BufferedOutputStream(fos);
				GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(bos);
				TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {
			taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
			addDirectory(taos, chartDir, chartName, ignoreMatchers);
		}
	}

	private void addDirectory(TarArchiveOutputStream taos, File dir, String entryBase, List<PathMatcher> ignoreMatchers)
			throws IOException {
		Path dirPath = dir.toPath();
		try (Stream<Path> paths = Files.walk(dirPath)) {
			paths.forEach((path) -> {
				try {
					File file = path.toFile();
					if (file.isDirectory()) {
						return;
					}
					Path relativePath = dirPath.relativize(path);
					if (isIgnored(relativePath, ignoreMatchers)) {
						return;
					}
					String entryName = entryBase + "/" + relativePath;
					TarArchiveEntry entry = new TarArchiveEntry(file, entryName);
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

	static List<PathMatcher> loadHelmIgnore(File chartDir) {
		File ignoreFile = new File(chartDir, ".helmignore");
		if (!ignoreFile.exists()) {
			return List.of();
		}
		List<PathMatcher> matchers = new ArrayList<>();
		FileSystem fs = FileSystems.getDefault();
		try {
			List<String> lines = Files.readAllLines(ignoreFile.toPath());
			for (String line : lines) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) {
					continue;
				}
				// Remove trailing slashes (directory markers) for matching
				if (trimmed.endsWith("/")) {
					trimmed = trimmed.substring(0, trimmed.length() - 1);
				}
				// Convert to glob: match anywhere in the path
				String glob = "glob:**/" + trimmed;
				matchers.add(fs.getPathMatcher(glob));
				// Also match at the root (no leading directory)
				matchers.add(fs.getPathMatcher("glob:" + trimmed));
			}
		}
		catch (IOException ex) {
			if (log.isWarnEnabled()) {
				log.warn("Failed to read .helmignore: {}", ex.getMessage());
			}
		}
		return matchers;
	}

	private static boolean isIgnored(Path relativePath, List<PathMatcher> matchers) {
		for (PathMatcher matcher : matchers) {
			if (matcher.matches(relativePath)) {
				return true;
			}
		}
		return false;
	}

	private static class UncheckedIOException extends RuntimeException {

		private final IOException cause;

		UncheckedIOException(IOException cause) {
			super(cause);
			this.cause = cause;
		}

		@Override
		public IOException getCause() {
			return this.cause;
		}

	}

}
