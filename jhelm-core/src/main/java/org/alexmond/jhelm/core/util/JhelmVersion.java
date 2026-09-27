package org.alexmond.jhelm.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

/**
 * The jhelm build version, as known to jhelm-core itself.
 * <p>
 * jhelm reports two different versions (#828). This class is the <em>provenance</em> one:
 * which jhelm build rendered a release, exposed to templates as
 * {@code .Capabilities.JhelmVersion}. The other is the Helm <em>compatibility</em>
 * version in {@code .Capabilities.HelmVersion}, which charts gate on (e.g.
 * {@code semverCompare ">=3.0.0"}) and which is configured separately.
 * <p>
 * The version is read from {@code jhelm-version.properties}, filtered from the Maven
 * project version at build time, so it is available to every embedder (CLI, REST, MCP,
 * libraries) and not only to the Spring Boot CLI's build-info. It falls back to the jar
 * manifest's {@code Implementation-Version}, and finally to a development marker.
 */
@Slf4j
public final class JhelmVersion {

	/** Returned when no version source is available (e.g. an unfiltered IDE build). */
	public static final String DEVELOPMENT = "development";

	private static final String RESOURCE = "/org/alexmond/jhelm/core/jhelm-version.properties";

	private static final String CURRENT = resolve();

	private JhelmVersion() {
	}

	/**
	 * Returns the jhelm version, e.g. {@code 1.5.1}.
	 * @return the version, never {@code null}
	 */
	public static String current() {
		return CURRENT;
	}

	/**
	 * Adds the leading {@code v} Helm uses in {@code .Capabilities} version strings.
	 * @param version a version such as {@code 1.5.1} or {@code v1.5.1}
	 * @return the version in {@code vX.Y.Z} form, trimmed
	 */
	public static String withLeadingV(String version) {
		String trimmed = version.trim();
		return trimmed.startsWith("v") ? trimmed : "v" + trimmed;
	}

	private static String resolve() {
		String fromResource = fromResource();
		if (fromResource != null) {
			return fromResource;
		}
		String manifest = JhelmVersion.class.getPackage().getImplementationVersion();
		return (manifest != null && !manifest.isBlank()) ? manifest : DEVELOPMENT;
	}

	private static String fromResource() {
		try (InputStream in = JhelmVersion.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				return null;
			}
			Properties props = new Properties();
			props.load(in);
			String version = props.getProperty("version");
			// an unfiltered copy still holds the @project.version@ placeholder
			return (version != null && !version.isBlank() && !version.contains("@")) ? version.trim() : null;
		}
		catch (IOException ex) {
			log.debug("Could not read {}", RESOURCE, ex);
			return null;
		}
	}

}
