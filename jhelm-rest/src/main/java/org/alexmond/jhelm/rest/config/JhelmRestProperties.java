package org.alexmond.jhelm.rest.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

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
	 * Maximum size of an uploaded chart archive (applied to both the multipart file and
	 * the whole request), capping upload-based resource use. Defaults to 100 MB.
	 */
	private DataSize maxUploadSize = DataSize.ofMegabytes(100);

	/**
	 * Configuration for jhelm's built-in access-mode interceptor that gates
	 * cluster-mutating REST endpoints.
	 */
	private AccessInterceptor accessInterceptor = new AccessInterceptor();

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

	/**
	 * Settings for jhelm's built-in access-mode interceptor.
	 */
	@Getter
	@Setter
	public static class AccessInterceptor {

		/**
		 * Whether jhelm registers its built-in access-mode interceptor, which gates
		 * cluster-mutating REST endpoints behind the shared {@code jhelm.security.*}
		 * policy (deny-by-default). Set to {@code false} when the host application embeds
		 * jhelm-rest under its own Spring Security so mutating operations are not
		 * double-gated; the interceptor is then not registered and requests pass through
		 * to the host's own security. Defaults to {@code true}.
		 */
		private boolean enabled = true;

	}

}
