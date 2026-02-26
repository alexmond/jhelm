package org.alexmond.jhelm.plugin.spi;

import org.alexmond.jhelm.plugin.exception.PluginExecutionException;

/**
 * Plugin that fetches charts from non-standard protocols (S3, Git, custom registries).
 */
public interface DownloaderPlugin extends Plugin {

	/**
	 * Check whether this plugin supports the given protocol.
	 * @param protocol the protocol string (e.g., "s3")
	 * @return {@code true} if supported
	 */
	boolean supportsProtocol(String protocol);

	/**
	 * Download content from the given URL.
	 * @param url the URL to download from
	 * @return the raw bytes
	 * @throws PluginExecutionException if the download fails
	 */
	byte[] download(String url) throws PluginExecutionException;

}
