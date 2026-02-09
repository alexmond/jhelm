package org.alexmond.jhelm.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

@Slf4j
public class RepoManager {
    private final ObjectMapper yamlMapper;
    private final String configPath;
    private boolean insecureSkipTlsVerify = false;

    public void setInsecureSkipTlsVerify(boolean insecureSkipTlsVerify) {
        this.insecureSkipTlsVerify = insecureSkipTlsVerify;
    }

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
            return RepositoryConfig.builder()
                    .repositories(new ArrayList<>())
                    .apiVersion("")
                    .generated(OffsetDateTime.now().toString())
                    .build();
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
        RepositoryConfig.Repository repo = RepositoryConfig.Repository.builder()
                .name(name)
                .url(url)
                .build();
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

    public void pull(String chartName, String repoName, String version, String destDir) throws IOException {
        RepositoryConfig config = loadConfig();
        RepositoryConfig.Repository repo = config.getRepositories().stream()
                .filter(r -> r.getName().equals(repoName))
                .findFirst()
                .orElseThrow(() -> new IOException("Repository not found: " + repoName));

        // Simplified: assuming chart is available at repoUrl/chartName-version.tgz
        // In reality, we should parse index.yaml
        String chartUrl = repo.getUrl() + "/" + chartName + "-" + version + ".tgz";
        pullFromUrl(chartUrl, destDir, chartName + "-" + version + ".tgz");
    }

    public void pullFromUrl(String chartUrl, String destDir, String fileName) throws IOException {
        log.info("Pulling chart from {} to {}", chartUrl, destDir);

        File destFile = new File(destDir, fileName);
        destFile.getParentFile().mkdirs();

        URL url = new URL(chartUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (insecureSkipTlsVerify && conn instanceof HttpsURLConnection httpsConn) {
            setupInsecureSsl(httpsConn);
        }
        conn.setRequestProperty("User-Agent", "jhelm");
        
        try (InputStream in = conn.getInputStream();
             ReadableByteChannel rbc = Channels.newChannel(in);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
        log.info("Chart pulled successfully to {}", destFile.getAbsolutePath());
    }

    private void setupInsecureSsl(HttpsURLConnection conn) {
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            }}, new java.security.SecureRandom());
            conn.setSSLSocketFactory(sc.getSocketFactory());
            conn.setHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            log.error("Failed to setup insecure SSL", e);
        }
    }

    public void untar(File tgzFile, File destDir) throws IOException {
        log.info("Untarring {} to {}", tgzFile.getAbsolutePath(), destDir.getAbsolutePath());
        try (InputStream fi = Files.newInputStream(tgzFile.toPath());
             InputStream bi = new BufferedInputStream(fi);
             InputStream gzi = new GzipCompressorInputStream(bi);
             TarArchiveInputStream ti = new TarArchiveInputStream(gzi)) {

            TarArchiveEntry entry;
            while ((entry = ti.getNextEntry()) != null) {
                if (!ti.canReadEntryData(entry)) {
                    continue;
                }
                File f = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!f.isDirectory() && !f.mkdirs()) {
                        throw new IOException("failed to create directory " + f);
                    }
                } else {
                    File parent = f.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("failed to create directory " + parent);
                    }
                    try (OutputStream o = Files.newOutputStream(f.toPath())) {
                        ti.transferTo(o);
                    }
                }
            }
        }
    }
}
