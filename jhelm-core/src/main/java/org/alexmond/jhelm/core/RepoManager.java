package org.alexmond.jhelm.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;

@Slf4j
public class RepoManager {
    private final ObjectMapper yamlMapper;
    private final String configPath;

    public RepoManager() {
        YAMLFactory yamlFactory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        this.yamlMapper = new ObjectMapper(yamlFactory);
        
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            this.configPath = Paths.get(home, "Library/Preferences/helm/repositories.yaml").toString();
        } else if (os.contains("win")) {
            this.configPath = Paths.get(System.getenv("APPDATA"), "helm/repositories.yaml").toString();
        } else {
            this.configPath = Paths.get(home, ".config/helm/repositories.yaml").toString();
        }
    }

    public RepositoryConfig loadConfig() throws IOException {
        File file = new File(configPath);
        if (!file.exists()) {
            RepositoryConfig config = new RepositoryConfig();
            config.setRepositories(new ArrayList<>());
            config.setApiVersion("");
            config.setGenerated(OffsetDateTime.now().toString());
            return config;
        }
        return yamlMapper.readValue(file, RepositoryConfig.class);
    }

    public void saveConfig(RepositoryConfig config) throws IOException {
        File file = new File(configPath);
        file.getParentFile().mkdirs();
        yamlMapper.writeValue(file, config);
    }

    public void addRepo(String name, String url) throws IOException {
        RepositoryConfig config = loadConfig();
        config.getRepositories().removeIf(r -> r.getName().equals(name));
        RepositoryConfig.Repository repo = new RepositoryConfig.Repository();
        repo.setName(name);
        repo.setUrl(url);
        config.getRepositories().add(repo);
        config.setGenerated(OffsetDateTime.now().toString());
        saveConfig(config);
    }

    public void removeRepo(String name) throws IOException {
        RepositoryConfig config = loadConfig();
        config.getRepositories().removeIf(r -> r.getName().equals(name));
        config.setGenerated(OffsetDateTime.now().toString());
        saveConfig(config);
    }
}
