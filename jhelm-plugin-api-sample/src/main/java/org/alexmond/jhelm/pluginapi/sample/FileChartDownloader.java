package org.alexmond.jhelm.pluginapi.sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.alexmond.jhelm.pluginapi.JhelmChartDownloader;
import org.alexmond.jhelm.pluginapi.JhelmPluginException;

/**
 * Sample {@link JhelmChartDownloader} for a {@code samplefile://} scheme that reads a
 * chart archive from the local filesystem. A real downloader would fetch from S3, GCS, an
 * artifact registry, or another backend.
 */
public class FileChartDownloader implements JhelmChartDownloader {

	private static final String SCHEME = "samplefile";

	@Override
	public String name() {
		return "samplefile";
	}

	@Override
	public boolean supports(String scheme) {
		return SCHEME.equalsIgnoreCase(scheme);
	}

	@Override
	public byte[] download(String url) throws JhelmPluginException {
		int schemeEnd = url.indexOf("://");
		String path = (schemeEnd > 0) ? url.substring(schemeEnd + 3) : url;
		try {
			return Files.readAllBytes(Path.of(path));
		}
		catch (IOException | RuntimeException ex) {
			throw new JhelmPluginException("failed to read " + url + ": " + ex.getMessage(), ex);
		}
	}

}
