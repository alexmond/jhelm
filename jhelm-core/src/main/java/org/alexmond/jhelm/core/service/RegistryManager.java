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
		String home = System.getProperty("user.home");
		String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
		if (os.contains("mac")) {
			this.configPath = Paths.get(home, "Library/Preferences/helm/registry/config.json").toString();
		}
		else if (os.contains("win")) {
			this.configPath = Paths.get(System.getenv("APPDATA"), "helm/registry/config.json").toString();
		}
		else {
			this.configPath = Paths.get(home, ".config/helm/registry/config.json").toString();
		}
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
		dir.mkdirs();
		// The registry config holds base64 credentials — keep it owner-only (0600), and
		// the containing directory 0700, mirroring how helm/docker protect their auth
		// files. Best-effort: POSIX only, and a failure to tighten must not lose the
		// save.
		restrictPermissions(dir.toPath(), "rwx------");
		jsonMapper.writeValue(file, config);
		restrictPermissions(file.toPath(), "rw-------");
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
