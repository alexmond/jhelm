package org.alexmond.jhelm.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class RegistryManager {
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final String configPath;

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

    public RegistryManager() {
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            this.configPath = Paths.get(home, "Library/Preferences/helm/registry/config.json").toString();
        } else if (os.contains("win")) {
            this.configPath = Paths.get(System.getenv("APPDATA"), "helm/registry/config.json").toString();
        } else {
            this.configPath = Paths.get(home, ".config/helm/registry/config.json").toString();
        }
    }

    public void login(String registry, String username, String password) throws IOException {
        Config config = loadConfig();
        String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        Config.Auth authObj = Config.Auth.builder()
                .auth(auth)
                .build();
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
        return auth != null ? auth.getAuth() : null;
    }

    private void saveConfig(Config config) throws IOException {
        File file = new File(configPath);
        file.getParentFile().mkdirs();
        jsonMapper.writeValue(file, config);
    }
}
