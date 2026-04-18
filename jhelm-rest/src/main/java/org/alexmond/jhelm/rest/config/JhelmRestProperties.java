package org.alexmond.jhelm.rest.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the jhelm REST API module.
 */
@Getter
@Setter
@Slf4j
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
	 * Returns the configured temp directory, or the system default if not set. The
	 * returned path is always absolute and normalized.
	 * @return the temp directory path
	 */
	public Path getTempDir() {
		Path dir = (this.tempDir != null) ? this.tempDir : Path.of(System.getProperty("java.io.tmpdir"));
		return dir.toAbsolutePath().normalize();
	}

	@PostConstruct
	void validateWorkspace() throws IOException {
		Path workspace = getTempDir();
		Files.createDirectories(workspace);
		log.info("jhelm workspace: {}", workspace);
	}

}
