package org.alexmond.jhelm.core.service;

import tools.jackson.databind.json.JsonMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

@Slf4j
public class RegistryManager {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final String configPath;

	public RegistryManager() {
		this(resolveDefaultConfigPath());
	}

	/**
	 * Creates a manager backed by an explicit config file. Used to honor
	 * {@code jhelm.registry-config-path} and, in tests, to point at a temporary file
	 * instead of the real per-user helm registry config.
	 * @param configPath the path to the registry credentials file
	 */
	public RegistryManager(String configPath) {
		this.configPath = configPath;
	}

	/**
	 * Resolves the registry config.json path the way Helm does:
	 * {@code $HELM_REGISTRY_CONFIG} when set, otherwise the per-OS Helm default
	 * (docker-style credentials, shared with {@code helm registry login}).
	 * Package-private for testing.
	 * @param helmRegistryConfig the value of {@code $HELM_REGISTRY_CONFIG} (may be
	 * {@code null})
	 * @param home the user home directory
	 * @param osName the OS name
	 * @return the resolved registry config.json path
	 */
	static String resolveConfigPath(String helmRegistryConfig, String home, String osName) {
		if (helmRegistryConfig != null && !helmRegistryConfig.isBlank()) {
			return helmRegistryConfig;
		}
		String os = osName.toLowerCase(Locale.ROOT);
		if (os.contains("mac")) {
			return Paths.get(home, "Library/Preferences/helm/registry/config.json").toString();
		}
		if (os.contains("win")) {
			return Paths.get(System.getenv("APPDATA"), "helm/registry/config.json").toString();
		}
		return Paths.get(home, ".config/helm/registry/config.json").toString();
	}

	private static String resolveDefaultConfigPath() {
		return resolveConfigPath(System.getenv("HELM_REGISTRY_CONFIG"), System.getProperty("user.home"),
				System.getProperty("os.name"));
	}

	/**
	 * Returns the effective registry {@code config.json} path this manager reads and
	 * writes.
	 * @return the registry config path
	 */
	public String getConfigPath() {
		return this.configPath;
	}

	public void login(String registry, String username, String password) throws IOException {
		Config config = loadConfig();
		String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
		Config.Auth authObj = Config.Auth.builder().auth(auth).build();
		config.getAuths().put(registry, authObj);
		saveConfig(config);
	}

	public void logout(String registry) throws IOException {
		Config config = loadConfig();
		config.getAuths().remove(registry);
		saveConfig(config);
	}

	public Config loadConfig() throws IOException {
		File file = new File(configPath);
		if (!file.exists()) {
			return Config.builder().build();
		}
		return jsonMapper.readValue(file, Config.class);
	}

	public String getAuth(String registry) throws IOException {
		Config config = loadConfig();
		Config.Auth auth = config.getAuths().get(registry);
		return (auth != null) ? auth.getAuth() : null;
	}

	private void saveConfig(Config config) throws IOException {
		File file = new File(configPath);
		File dir = file.getParentFile();
		// The registry config holds base64 credentials — keep it owner-only (0600), and
		// the containing directory 0700, mirroring how helm/docker protect their auth
		// files. Best-effort: POSIX only, and a failure to tighten must not lose the
		// save.
		if (dir != null) {
			dir.mkdirs();
			restrictPermissions(dir.toPath(), "rwx------");
		}
		// Create the file with owner-only perms BEFORE writing the credentials, so there
		// is no window where it exists on disk with default (looser) permissions.
		ensureOwnerOnlyFile(file.toPath());
		jsonMapper.writeValue(file, config);
	}

	/**
	 * Ensures the credentials file exists with owner-only ({@code 0600}) permissions
	 * before it is written. A new file is created atomically with the restrictive mode
	 * (closing the create-then-chmod TOCTOU window); an existing file is re-tightened.
	 * Best-effort on non-POSIX filesystems.
	 * @param path the credentials file path
	 * @throws IOException if the file cannot be created
	 */
	private static void ensureOwnerOnlyFile(Path path) throws IOException {
		boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
		if (Files.notExists(path)) {
			if (posix) {
				Files.createFile(path,
						PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
			}
			else {
				Files.createFile(path);
			}
		}
		else {
			restrictPermissions(path, "rw-------");
		}
	}

	private static void restrictPermissions(Path path, String posix) {
		if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
			return;
		}
		try {
			Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(posix));
		}
		catch (IOException | UnsupportedOperationException ex) {
			log.warn("Could not restrict permissions on {}: {}", path, ex.getMessage());
		}
	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Config {

		@Builder.Default
		private Map<String, Auth> auths = new HashMap<>();

		@Data
		@Builder
		@NoArgsConstructor
		@AllArgsConstructor
		public static class Auth {

			private String auth;

		}

	}

}
