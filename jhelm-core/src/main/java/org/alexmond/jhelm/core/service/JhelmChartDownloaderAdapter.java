package org.alexmond.jhelm.core.service;

import java.io.IOException;

import org.alexmond.jhelm.pluginapi.JhelmChartDownloader;
import org.alexmond.jhelm.pluginapi.JhelmPluginException;

/**
 * Adapts a Java {@link JhelmChartDownloader} plugin to the internal
 * {@link ChartDownloader} SPI consulted by {@code RepoManager}, translating a
 * {@link JhelmPluginException} into the {@link IOException} the fetch path expects.
 */
public class JhelmChartDownloaderAdapter implements ChartDownloader {

	private final JhelmChartDownloader plugin;

	/**
	 * Wraps a chart-downloader plugin.
	 * @param plugin the Java chart-downloader plugin
	 */
	public JhelmChartDownloaderAdapter(JhelmChartDownloader plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean supportsProtocol(String protocol) {
		return this.plugin.supports(protocol);
	}

	@Override
	public byte[] download(String url) throws IOException {
		try {
			return this.plugin.download(url);
		}
		catch (JhelmPluginException ex) {
			throw new IOException("chart-downloader plugin '" + this.plugin.name() + "' failed: " + ex.getMessage(),
					ex);
		}
	}

}
