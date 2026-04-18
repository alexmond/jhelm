package org.alexmond.jhelm.rest.util;

import java.io.File;
import java.nio.file.Path;

import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.RepoManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Resolves a chart source (repository reference or uploaded .tgz) to a loaded
 * {@link Chart} inside a {@link TempDir}.
 */
public final class ChartSourceResolver {

	private ChartSourceResolver() {
	}

	/**
	 * Pull a chart from a repository and load it.
	 * @param chartRef chart reference (e.g. "bitnami/nginx" or "oci://...")
	 * @param version optional version constraint
	 * @param repoManager repository manager for pulling
	 * @param chartLoader loader to parse the chart directory
	 * @param tempDir temporary directory to pull into
	 * @return the loaded chart
	 */
	public static Chart fromChartRef(String chartRef, String version, RepoManager repoManager, ChartLoader chartLoader,
			TempDir tempDir) throws Exception {
		repoManager.pull(chartRef, version, tempDir.path().toString());
		Path chartDir = ChartLoader.findChartDir(tempDir.path());
		return chartLoader.load(chartDir.toFile());
	}

	/**
	 * Extract an uploaded .tgz chart archive and load it.
	 * @param file the uploaded .tgz file
	 * @param repoManager repository manager (for untar utility)
	 * @param chartLoader loader to parse the chart directory
	 * @param tempDir temporary directory to extract into
	 * @return the loaded chart
	 */
	public static Chart fromUpload(MultipartFile file, RepoManager repoManager, ChartLoader chartLoader,
			TempDir tempDir) throws Exception {
		File tgzFile = tempDir.path().resolve("upload.tgz").toFile();
		file.transferTo(tgzFile);
		repoManager.untar(tgzFile, tempDir.path().toFile());
		Path chartDir = ChartLoader.findChartDir(tempDir.path());
		return chartLoader.load(chartDir.toFile());
	}

}
