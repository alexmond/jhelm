package org.alexmond.jhelm.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;

@Slf4j
public class RepoManager {
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final String configPath;
    private CloseableHttpClient httpClient;
    @Setter
    private boolean insecureSkipTlsVerify = false;
    @Setter
    private RegistryManager registryManager;

    public RepoManager() {
        org.yaml.snakeyaml.LoaderOptions loaderOptions = new org.yaml.snakeyaml.LoaderOptions();
        loaderOptions.setCodePointLimit(50_000_000); // 50MB
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .loaderOptions(loaderOptions)
                .build();

        com.fasterxml.jackson.core.StreamReadConstraints constraints = com.fasterxml.jackson.core.StreamReadConstraints.builder()
                .maxStringLength(50_000_000)
                .build();
        yamlFactory.setStreamReadConstraints(constraints);

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

        initHttpClient();
    }

    private void initHttpClient() {
        try {
//            if (insecureSkipTlsVerify) {
//                TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
//                SSLContext sslContext = SSLContextBuilder.create()
//                        .loadTrustMaterial(null, acceptingTrustStrategy)
//                        .build();
//                TlsConfig tlsConfig = TlsConfig.custom()
//                        .setSslContext(sslContext)
//                        .build();
//                var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
//                        .setDefaultTlsConfig(tlsConfig)
//                        .build();
//                httpClient = HttpClients.custom()
//                        .setConnectionManager(connectionManager)
//                        .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
//                        .build();
//            } else {
            httpClient = HttpClients.createDefault();
//            }
        } catch (Exception e) {
            log.error("Failed to initialize HTTP client", e);
            httpClient = HttpClients.createDefault();
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
        // Eagerly update index on add for convenience, like `helm repo add` often followed by update
        try {
            updateRepo(name);
        } catch (IOException e) {
            log.warn("Failed to update repo '{}' immediately after add: {}", name, e.getMessage());
        }
    }

    public void removeRepo(String name) throws IOException {
        RepositoryConfig config = loadConfig();
        config.getRepositories().removeIf(r -> r.getName().equals(name));
        config.setGenerated(OffsetDateTime.now().toString());
        saveConfig(config);
    }

    public String getRepoUrl(String name) throws IOException {
        RepositoryConfig config = loadConfig();
        return config.getRepositories().stream()
                .filter(r -> r.getName().equals(name))
                .map(RepositoryConfig.Repository::getUrl)
                .findFirst()
                .orElse(null);
    }

    private File getCacheDir() {
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
        File base;
        if (os.contains("mac")) {
            base = Paths.get(home, "Library/Caches/jhelm/repository").toFile();
        } else if (os.contains("win")) {
            base = Paths.get(System.getenv("LOCALAPPDATA"), "jhelm/repository").toFile();
        } else {
            base = Paths.get(home, ".cache/jhelm/repository").toFile();
        }
        base.mkdirs();
        return base;
    }

    private File getIndexCacheFile(String repoName) {
        return new File(getCacheDir(), repoName + "-index.yaml");
    }

    public void updateRepo(String name) throws IOException {
        String repoUrl = getRepoUrl(name);
        if (repoUrl == null) {
            throw new IOException("Repository not found: " + name);
        }
        String indexUrl = repoUrl.endsWith("/") ? repoUrl + "index.yaml" : repoUrl + "/index.yaml";
        log.info("Updating repository '{}' from {}", name, indexUrl);

        HttpGet httpGet = new HttpGet(indexUrl);
        httpGet.setHeader("User-Agent", "jhelm");

        httpClient.execute(httpGet, response -> {
            int statusCode = response.getCode();
            if (statusCode != 200) {
                throw new IOException("Failed to download index from " + indexUrl + ": " + statusCode + " " + response.getReasonPhrase());
            }
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try (InputStream in = entity.getContent();
                     OutputStream out = new FileOutputStream(getIndexCacheFile(name))) {
                    in.transferTo(out);
                }
            }
            return null;
        });
        log.info("Repository '{}' index updated", name);
    }

    public void updateAll() throws IOException {
        RepositoryConfig config = loadConfig();
        for (RepositoryConfig.Repository r : config.getRepositories()) {
            try {
                updateRepo(r.getName());
            } catch (IOException e) {
                log.warn("Failed to update repo {}: {}", r.getName(), e.getMessage());
            }
        }
    }

    public java.util.List<ChartVersion> getChartVersions(String repoName, String chartName) throws IOException {
        // Prefer cached index if exists, else fetch live
        File indexFile = getIndexCacheFile(repoName);
        InputStream indexIn;
        if (indexFile.exists()) {
            indexIn = new FileInputStream(indexFile);
        } else {
            String repoUrl = getRepoUrl(repoName);
            if (repoUrl == null) {
                // If repoName is null or empty, it might be an absolute URL pull or something else,
                // but for getChartVersions we need a repo.
                throw new IOException("Repository name is required to get chart versions. Found: " + repoName);
            }
            String indexUrl = repoUrl.endsWith("/") ? repoUrl + "index.yaml" : repoUrl + "/index.yaml";
            log.info("Downloading index from {} ...", indexUrl);

            HttpGet httpGet = new HttpGet(indexUrl);
            httpGet.setHeader("User-Agent", "jhelm");

            // Download to byte array to avoid stream lifecycle issues
            byte[] indexData = httpClient.execute(httpGet, response -> {
                int statusCode = response.getCode();
                if (statusCode != 200) {
                    throw new IOException("Failed to download index from " + indexUrl + ": " + statusCode);
                }
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    return entity.getContent().readAllBytes();
                } else {
                    throw new IOException("Empty response from " + indexUrl);
                }
            });
            indexIn = new java.io.ByteArrayInputStream(indexData);
        }
        java.util.List<ChartVersion> result = new java.util.ArrayList<>();
        try (InputStream in = indexIn) {
            // Parse YAML as Map<String,Object>
            java.util.Map<?, ?> root = yamlMapper.readValue(in, java.util.Map.class);
            Object entriesObj = root.get("entries");
            if (!(entriesObj instanceof java.util.Map<?, ?> entries)) {
                return result;
            }
            Object chartListObj = entries.get(chartName);
            if (!(chartListObj instanceof java.util.List<?> list)) {
                return result;
            }
            for (Object o : list) {
                if (o instanceof java.util.Map<?, ?> m) {
                    String version = asString(m.get("version"));
                    String appVersion = asString(m.get("appVersion"));
                    String description = asString(m.get("description"));
                    result.add(new ChartVersion(repoName + "/" + chartName, version, appVersion, description));
                }
            }
        }
        // Helm shows latest first by default for --versions output, so sort descending semver-ish (string fallback)
        result.sort((a, b) -> safeCompareVersions(b.getChartVersion(), a.getChartVersion()));
        return result;
    }

    private int safeCompareVersions(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;
        // simple split by dots, compare numerically when possible, else lexicographically
        String[] p1 = v1.split("[.-]");
        String[] p2 = v2.split("[.-]");
        int n = Math.max(p1.length, p2.length);
        for (int i = 0; i < n; i++) {
            String a = i < p1.length ? p1[i] : "0";
            String b = i < p2.length ? p2[i] : "0";
            int ai = parseIntSafe(a);
            int bi = parseIntSafe(b);
            if (ai != Integer.MIN_VALUE && bi != Integer.MIN_VALUE) {
                if (ai != bi) return Integer.compare(ai, bi);
            } else {
                int c = a.compareTo(b);
                if (c != 0) return c;
            }
        }
        return 0;
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public void pull(String chartFullName, String repoName, String version, String destDir) throws IOException {
        String finalChartName = chartFullName;
        String finalRepoName = repoName;

        if (chartFullName.contains("/")) {
            int slashIndex = chartFullName.lastIndexOf("/");
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

        // Try to find the actual URL from the index.yaml if available
        String chartUrl = null;
        File indexFile = getIndexCacheFile(finalRepoName);
        if (indexFile.exists()) {
            try (InputStream in = new FileInputStream(indexFile)) {
                java.util.Map<?, ?> root = yamlMapper.readValue(in, java.util.Map.class);
                java.util.Map<?, ?> entries = (java.util.Map<?, ?>) root.get("entries");
                if (entries != null) {
                    java.util.List<?> versions = (java.util.List<?>) entries.get(finalChartName);
                    if (versions != null) {
                        for (Object o : versions) {
                            java.util.Map<?, ?> m = (java.util.Map<?, ?>) o;
                            if (version.equals(asString(m.get("version")))) {
                                java.util.List<?> urls = (java.util.List<?>) m.get("urls");
                                if (urls != null && !urls.isEmpty()) {
                                    chartUrl = asString(urls.get(0));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (chartUrl == null) {
            // Fallback to convention
            chartUrl = repo.getUrl() + "/" + finalChartName + "-" + version + ".tgz";
        } else if (!chartUrl.contains("://")) {
            // Handle relative URLs
            String base = repo.getUrl();
            if (!base.endsWith("/")) base += "/";
            chartUrl = base + chartUrl;
        }

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

        HttpGet httpGet = new HttpGet(chartUrl);
        httpGet.setHeader("User-Agent", "jhelm");

        httpClient.execute(httpGet, response -> {
            int statusCode = response.getCode();
            if (statusCode != 200) {
                throw new IOException("Failed to download chart from " + chartUrl + ": " + statusCode);
            }
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try (InputStream in = entity.getContent();
                     FileOutputStream fos = new FileOutputStream(destFile)) {
                    in.transferTo(fos);
                }
            }
            return null;
        });
        log.info("Chart pulled successfully to {}", destFile.getAbsolutePath());

        // Automatically untar regular charts too
        untar(destFile, new File(destDir));
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

        // 1. Get Token
        String token = null;
        String auth = null;
        if (registryManager != null) {
            auth = registryManager.getAuth(registry);
        }
        token = fetchOciToken(registry, path, auth);

        // 2. Get Manifest
        String manifestUrl = "https://" + registry + "/v2/" + path + "/manifests/" + tag;
        JsonNode manifest = null;
        try {
            manifest = callOciApi(manifestUrl, token, "application/vnd.oci.image.manifest.v1+json");
        } catch (IOException e) {
            log.warn("Failed to get OCI manifest with v1+json, trying without specific accept header: {}", e.getMessage());
            manifest = callOciApi(manifestUrl, token, null);
        }

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

        // 5. Untar it automatically to match helm behavior
        File tgzFile = new File(destDir, fileName);
        untar(tgzFile, new File(destDir));
    }

    private String fetchOciToken(String registry, String path, String auth) throws IOException {
        // Standard Docker/OCI bearer token flow.
        // If it's registry-1.docker.io, use auth.docker.io
        String tokenService = registry;
        String tokenUrlPrefix = "https://" + registry + "/v2/token";
        if ("registry-1.docker.io".equals(registry)) {
            tokenUrlPrefix = "https://auth.docker.io/token";
            tokenService = "registry.docker.io";
        }

        String url = tokenUrlPrefix + "?service=" + tokenService + "&scope=repository:" + path + ":pull";
        HttpGet httpGet = new HttpGet(url);
        if (auth != null) {
            httpGet.setHeader("Authorization", "Basic " + auth);
        }

        try {
            return httpClient.execute(httpGet, response -> {
                int statusCode = response.getCode();
                if (statusCode != 200) {
                    log.warn("Failed to fetch OCI token: HTTP {}", statusCode);
                    return null;
                }

                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try (InputStream in = entity.getContent()) {
                        JsonNode node = jsonMapper.readTree(in);
                        return node.has("token") ? node.get("token").asText() : node.get("access_token").asText();
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Failed to parse OCI token: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode callOciApi(String urlStr, String token, String accept) throws IOException {
        HttpGet httpGet = new HttpGet(urlStr);
        if (token != null) {
            httpGet.setHeader("Authorization", "Bearer " + token);
        }
        if (accept != null) {
            httpGet.setHeader("Accept", accept);
        }

        return httpClient.execute(httpGet, response -> {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try (InputStream in = entity.getContent()) {
                    return jsonMapper.readTree(in);
                }
            }
            return null;
        });
    }

    private void downloadBlob(String urlStr, String token, String destDir, String fileName) throws IOException {
        File destFile = new File(destDir, fileName);
        destFile.getParentFile().mkdirs();

        HttpGet httpGet = new HttpGet(urlStr);
        if (token != null) {
            httpGet.setHeader("Authorization", "Bearer " + token);
        }

        httpClient.execute(httpGet, response -> {
            // Handle redirects (common for blobs stored in S3/GCS)
            int status = response.getCode();
            if (status == 301 || status == 302 || status == 307 || status == 308) {
                String newUrl = response.getFirstHeader("Location").getValue();
                downloadBlob(newUrl, null, destDir, fileName); // Redirection might not need the same token if it's a signed URL
                return null;
            }

            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try (InputStream in = entity.getContent();
                     FileOutputStream fos = new FileOutputStream(destFile)) {
                    in.transferTo(fos);
                }
            }
            return null;
        });
        log.info("OCI Blob downloaded successfully to {}", destFile.getAbsolutePath());
    }

    public void close() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.error("Failed to close HTTP client", e);
            }
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

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ChartVersion {
        private String name;         // repo/chart
        private String chartVersion; // version
        private String appVersion;   // appVersion (may be null)
        private String description;  // description (may be null)
    }
}
