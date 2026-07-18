package org.alexmond.jhelm.pluginapi;

/**
 * A plugin that fetches charts for a custom URL scheme (for example {@code s3://},
 * {@code gs://}) in Java — the in-process equivalent of a Helm downloader plugin. jhelm
 * consults it when a chart URL uses a scheme it {@link #supports(String) supports}.
 */
public interface JhelmChartDownloader extends JhelmPlugin {

	/**
	 * Reports whether this downloader handles the given URL scheme.
	 * @param scheme the URL scheme, without {@code ://} (for example {@code s3})
	 * @return {@code true} if this downloader can fetch that scheme
	 */
	boolean supports(String scheme);

	/**
	 * Downloads a chart archive.
	 * @param url the full chart URL (for example {@code s3://bucket/chart-1.0.0.tgz})
	 * @return the raw chart archive bytes (a gzipped tar)
	 * @throws JhelmPluginException if the download fails
	 */
	byte[] download(String url) throws JhelmPluginException;

}
