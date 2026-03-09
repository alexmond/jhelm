package org.alexmond.jhelm.rest.config;

import java.nio.file.Path;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the jhelm REST API module.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jhelm.rest")
public class JhelmRestProperties {

	/**
	 * Base path for all jhelm REST endpoints.
	 */
	private String basePath = "/api/v1";

	/**
	 * Base directory for temporary files. Defaults to the system temp directory.
	 */
	private Path tempDir;

	/**
	 * Returns the configured temp directory, or the system default if not set.
	 * @return the temp directory path
	 */
	public Path getTempDir() {
		return (this.tempDir != null) ? this.tempDir : Path.of(System.getProperty("java.io.tmpdir"));
	}

}
