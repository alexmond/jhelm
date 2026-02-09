package org.alexmond.jhelm.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.Setter;
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
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final String configPath;
    @Setter
    private boolean insecureSkipTlsVerify = false;
    @Setter
    private RegistryManager registryManager;

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

    public void pull(String chartFullName, String repoName, String version, String destDir) throws IOException {
        String finalChartName = chartFullName;
        String finalRepoName = repoName;

        if (chartFullName.contains("/") && (repoName == null || repoName.isEmpty())) {
            int slashIndex = chartFullName.indexOf("/");
            finalRepoName = chartFullName.substring(0, slashIndex);
            finalChartName = chartFullName.substring(slashIndex + 1);
        }

        if (finalRepoName == null || finalRepoName.isEmpty()) {
            throw new IOException("Repository name is required (either as argument or as prefix in chart name)");
        }

        RepositoryConfig config = loadConfig();
        String searchRepoName = finalRepoName;
        RepositoryConfig.Repository repo = config.getRepositories().stream()
                .filter(r -> r.getName().equals(searchRepoName))
                .findFirst()
                .orElseThrow(() -> new IOException("Repository not found: " + searchRepoName));

        // Simplified: assuming chart is available at repoUrl/chartName-version.tgz
        // In reality, we should parse index.yaml
        String chartUrl = repo.getUrl() + "/" + finalChartName + "-" + version + ".tgz";
        pullFromUrl(chartUrl, destDir, finalChartName + "-" + version + ".tgz");
    }

    public void pullFromUrl(String chartUrl, String destDir, String fileName) throws IOException {
        if (chartUrl.startsWith("oci://")) {
            pullOci(chartUrl, destDir, fileName);
            return;
        }
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

    public void pullOci(String ociUrl, String destDir, String fileName) throws IOException {
        log.info("Pulling OCI chart from {} to {}", ociUrl, destDir);
        // oci://registry/repo/chart:version or oci://registry/repo/chart (tag defaults to latest)
        String raw = ociUrl.substring(6);
        int firstSlash = raw.indexOf('/');
        if (firstSlash == -1) throw new IOException("Invalid OCI URL: " + ociUrl);
        String registry = raw.substring(0, firstSlash);
        String path = raw.substring(firstSlash + 1);
        String tag = "latest";
        if (path.contains(":")) {
            int colon = path.lastIndexOf(':');
            tag = path.substring(colon + 1);
            path = path.substring(0, colon);
        }

        // 1. Get Token (if registryManager is available)
        String token = null;
        if (registryManager != null) {
            String auth = registryManager.getAuth(registry);
            if (auth != null) {
                token = fetchOciToken(registry, path, auth);
            }
        }

        // 2. Get Manifest
        String manifestUrl = "https://" + registry + "/v2/" + path + "/manifests/" + tag;
        JsonNode manifest = callOciApi(manifestUrl, token, "application/vnd.oci.image.manifest.v1+json");

        // 3. Find chart layer
        String digest = null;
        if (manifest.has("layers")) {
            for (JsonNode layer : manifest.get("layers")) {
                String mediaType = layer.get("mediaType").asText();
                if ("application/vnd.cncf.helm.chart.content.v1.tar+gzip".equals(mediaType) ||
                    "application/vnd.oci.image.layer.v1.tar+gzip".equals(mediaType)) {
                    digest = layer.get("digest").asText();
                    break;
                }
            }
        }

        if (digest == null) {
            throw new IOException("No chart layer found in OCI manifest for " + ociUrl);
        }

        // 4. Download Layer (blob)
        String blobUrl = "https://" + registry + "/v2/" + path + "/blobs/" + digest;
        downloadBlob(blobUrl, token, destDir, fileName);
    }

    private String fetchOciToken(String registry, String path, String auth) throws IOException {
        // This is a simplified OCI auth challenge handler. 
        // In a real implementation, we should first try the request, get 401, parse WWW-Authenticate header.
        // For now, we assume standard Docker/OCI bearer token flow.
        String url = "https://" + registry + "/v2/token?service=" + registry + "&scope=repository:" + path + ":pull";
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        if (insecureSkipTlsVerify && conn instanceof HttpsURLConnection httpsConn) {
            setupInsecureSsl(httpsConn);
        }
        conn.setRequestProperty("Authorization", "Basic " + auth);
        try (InputStream in = conn.getInputStream()) {
            JsonNode node = jsonMapper.readTree(in);
            return node.get("token").asText();
        } catch (Exception e) {
            log.warn("Failed to fetch OCI token: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode callOciApi(String urlStr, String token, String accept) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (insecureSkipTlsVerify && conn instanceof HttpsURLConnection httpsConn) {
            setupInsecureSsl(httpsConn);
        }
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (accept != null) {
            conn.setRequestProperty("Accept", accept);
        }
        try (InputStream in = conn.getInputStream()) {
            return jsonMapper.readTree(in);
        }
    }

    private void downloadBlob(String urlStr, String token, String destDir, String fileName) throws IOException {
        File destFile = new File(destDir, fileName);
        destFile.getParentFile().mkdirs();

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (insecureSkipTlsVerify && conn instanceof HttpsURLConnection httpsConn) {
            setupInsecureSsl(httpsConn);
        }
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        
        // Handle redirects (common for blobs stored in S3/GCS)
        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
            String newUrl = conn.getHeaderField("Location");
            downloadBlob(newUrl, null, destDir, fileName); // Redirection might not need the same token if it's a signed URL
            return;
        }

        try (InputStream in = conn.getInputStream();
             ReadableByteChannel rbc = Channels.newChannel(in);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
        log.info("OCI Blob downloaded successfully to {}", destFile.getAbsolutePath());
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
