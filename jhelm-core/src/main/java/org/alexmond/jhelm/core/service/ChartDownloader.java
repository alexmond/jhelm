package org.alexmond.jhelm.core.service;

/**
 * Callback interface for custom chart downloading from non-standard protocols.
 */
public interface ChartDownloader {

	/**
	 * Check whether this downloader supports the given protocol.
	 * @param protocol the protocol string (e.g., "s3")
	 * @return {@code true} if supported
	 */
	boolean supportsProtocol(String protocol);

	/**
	 * Download content from the given URL.
	 * @param url the URL to download from
	 * @return the raw bytes
	 * @throws Exception if the download fails
	 */
	byte[] download(String url) throws Exception;

}
