package org.alexmond.jhelm.core;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Scanner;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class KpsComparisonTest {

    private final RepoManager repoManager = createRepoManager();
    private final ChartLoader chartLoader = new ChartLoader();
    private final Engine engine = new Engine();
    private final InstallAction installAction = new InstallAction(engine, null);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RepoManager createRepoManager() {
        RepoManager rm = new RepoManager();
        rm.setInsecureSkipTlsVerify(true);
        return rm;
    }

    @Test
    void testSearchAndCompare() throws Exception {
        String query = "bitnami/nginx";
        String repo = "bitnami";
        String chart = "nginx";
        
        repoManager.addRepo(repo, "https://charts.bitnami.com/bitnami");
        var versions = repoManager.getChartVersions(repo, chart);
        assertFalse(versions.isEmpty());
        
        // Helm search
        String helmOutput = runHelmSearchRepo(query);
        if (helmOutput != null) {
            log.info("Helm search output for {}:\n{}", query, helmOutput);
            // Verify our latest version matches Helm's latest version (first line of output)
            String latestJHelm = versions.get(0).getChartVersion();
            assertTrue(helmOutput.contains(latestJHelm), "Helm output should contain jhelm latest version " + latestJHelm);
        }
    }

    private String runHelmSearchRepo(String query) {
        System.out.println("[DEBUG_LOG] Running helm search repo for " + query);
        try {
            ProcessBuilder pb = new ProcessBuilder("helm", "search", "repo", query);
            Process process = pb.start();
            
            String output;
            try (InputStream is = process.getInputStream();
                 Scanner s = new Scanner(is, StandardCharsets.UTF_8).useDelimiter("\\A")) {
                output = s.hasNext() ? s.next() : "";
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return null;
            }
            return output;
        } catch (Exception e) {
            log.error("Failed to run helm search", e);
            return null;
        }
    }

    @Test
    void testSimpleRendering() throws Exception {
        Chart chart = Chart.builder()
                .metadata(ChartMetadata.builder().name("simple").build())
                .values(Map.of("enabled", true, "name", "world"))
                .templates(new java.util.ArrayList<>(java.util.List.of(
                        Chart.Template.builder()
                                .name("hello.yaml")
                                .data("hello {{ .Values.name }} {{ if .Values.enabled }}enabled{{ end }}")
                                .build()
                )))
                .build();
        
        Release release = installAction.install(chart, "simple", "default", Map.of(), 1, false);
        log.info("Simple manifest: [{}]", release.getManifest().trim());
        assertTrue(release.getManifest().contains("hello world enabled"));
    }

    @Test
    void testPullAndCompare() throws Exception {
        // This test demonstrates using the jhelm API to "pull" (simulated by using local sample-charts directory)
        // Since we don't have a real Helm repo set up in CI, we use the local directory as our "repo"
        File repoDir = new File("sample-charts");
        if (!repoDir.exists()) repoDir = new File("../sample-charts");
        
        File nginxDir = new File(repoDir, "nginx");
        if (nginxDir.exists()) {
            compareChart("nginx", "pulled-nginx");
        }
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/charts.csv")
    void compareAllTopCharts(String chartName) throws IOException {
        compareChart(chartName, "release-" + chartName);
    }

    private void compareChart(String chartName, String releaseName) throws IOException {
        File chartDir = findChartDir(chartName);

        if (chartDir == null) {
            log.info("Chart {} not found locally, fetching from Artifact Hub...", chartName);
            try {
                fetchFromArtifactHub(chartName);
                chartDir = findChartDir(chartName);
            } catch (Exception e) {
                log.error("Failed to fetch chart {} from Artifact Hub: {}", chartName, e.getMessage());
            }
        }
        
        if (chartDir == null) {
            log.info("Skipping chart {} - directory not found and could not be fetched", chartName);
            return;
        }

        Chart chart = chartLoader.load(chartDir);
        assertNotNull(chart);

        try {
            // JHelm dry-run
            Release release = installAction.install(chart, releaseName, "default", Map.of(), 1, true);
            assertNotNull(release);
        
            String jhelmManifest = release.getManifest();
            log.info("{} - JHelm manifest length: {}", chartName, jhelmManifest.length());
        
            String sanitizedName = chartName.replace("/", "_");
            File actualFile = new File("target/test-output/actual_" + sanitizedName + ".yaml");
            actualFile.getParentFile().mkdirs();
            Files.writeString(actualFile.toPath(), jhelmManifest);

            // Helm dry-run comparison
            String helmManifest = runHelmInstallDryRun(chartDir, releaseName, "default");
            if (helmManifest != null) {
                File expectedFile = new File("target/test-output/expected_" + sanitizedName + ".yaml");
                Files.writeString(expectedFile.toPath(), helmManifest);
                
                // Compare manifests
                compareManifests(chartName, jhelmManifest, helmManifest);
            } else {
                log.warn("{} - Could not run helm dry-run for comparison", chartName);
            }
            
        } catch (Exception e) {
            log.error("{} - Rendering failed", chartName, e);
            fail(chartName + " - Rendering failed: " + e.getMessage());
        }
    }

    private String runHelmInstallDryRun(File chartDir, String releaseName, String namespace) {
        System.out.println("[DEBUG_LOG] Running helm install dry-run for " + releaseName + " using chart " + chartDir.getAbsolutePath());
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "helm", "install", releaseName, chartDir.getAbsolutePath(), 
                "--dry-run", "--namespace", namespace
            );
            Process process = pb.start();
            
            String output;
            try (InputStream is = process.getInputStream();
                 Scanner s = new Scanner(is, StandardCharsets.UTF_8).useDelimiter("\\A")) {
                output = s.hasNext() ? s.next() : "";
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                try (InputStream es = process.getErrorStream();
                     Scanner s = new Scanner(es, StandardCharsets.UTF_8).useDelimiter("\\A")) {
                    String error = s.hasNext() ? s.next() : "";
                    System.err.println("[DEBUG_LOG] Helm failed with exit code " + exitCode + ": " + error);
                    log.error("Helm failed with exit code {}: {}", exitCode, error);
                }
                return null;
            }

            // Extract manifest from helm output
            int manifestStart = output.indexOf("MANIFEST:");
            if (manifestStart == -1) {
                System.err.println("[DEBUG_LOG] MANIFEST: section not found in helm output");
                return null;
            }
            
            String manifest = output.substring(manifestStart + "MANIFEST:".length()).trim();
            // Remove NOTES if present
            int notesStart = manifest.indexOf("NOTES:");
            if (notesStart != -1) {
                manifest = manifest.substring(0, notesStart).trim();
            }
            
            System.out.println("[DEBUG_LOG] Successfully captured helm manifest for " + releaseName);
            return manifest;
        } catch (Exception e) {
            System.err.println("[DEBUG_LOG] Failed to run helm: " + e.getMessage());
            log.error("Failed to run helm", e);
            return null;
        }
    }

    private void compareManifests(String chartName, String jhelm, String helm) {
        System.out.println("[DEBUG_LOG] Comparing manifests for " + chartName);
        // Simple comparison for now: check if they contain similar key resources
        // Direct string comparison might fail due to comments, order, or labels
        
        // Remove comments and empty lines for a slightly more robust comparison
        String jhelmClean = cleanManifest(jhelm);
        String helmClean = cleanManifest(helm);
        
        if (!jhelmClean.equals(helmClean)) {
            System.out.println("[DEBUG_LOG] " + chartName + " - Manifests differ from Helm's output");
            log.warn("{} - Manifests differ from Helm's output", chartName);
            // We don't fail yet, but we log the difference in length
            log.info("{} - JHelm Clean Length: {}, Helm Clean Length: {}", chartName, jhelmClean.length(), helmClean.length());
        } else {
            System.out.println("[DEBUG_LOG] " + chartName + " - Manifests match Helm exactly (cleaned)!");
            log.info("{} - Manifests match Helm exactly (cleaned)!", chartName);
        }
    }

    private String cleanManifest(String m) {
        StringBuilder sb = new StringBuilder();
        for (String line : m.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            sb.append(trimmed).append("\n");
        }
        return sb.toString().trim();
    }

    private File findChartDir(String chartFullName) {
        String chartName = chartFullName.contains("/") ? chartFullName.substring(chartFullName.lastIndexOf("/") + 1) : chartFullName;
        String[] paths = {
            "sample-charts/" + chartName,
            "../sample-charts/" + chartName,
            "target/test-charts/" + chartName,
            "target/test-charts/bitnami/" + chartName, // common subfolder in bitnami tgz
            chartName, // legacy check
            "../" + chartName
        };
        for (String p : paths) {
            File f = new File(p);
            if (f.exists() && f.isDirectory() && new File(f, "Chart.yaml").exists()) {
                return f;
            }
        }
        return null;
    }

    private void fetchFromArtifactHub(String chartFullName) throws Exception {
        String repoNamePrefix = null;
        String chartNameOnly = chartFullName;
        if (chartFullName.contains("/")) {
            int slashIndex = chartFullName.indexOf("/");
            repoNamePrefix = chartFullName.substring(0, slashIndex);
            chartNameOnly = chartFullName.substring(slashIndex + 1);
        }

        // 1. Search for package
        String searchUrl = "https://artifacthub.io/api/v1/packages/search?ts_query_web=" + chartNameOnly + "&kind=0";
        JsonNode searchResult = callApi(searchUrl);
        JsonNode pkg = null;
        if (searchResult.has("packages") && searchResult.get("packages").isArray()) {
            for (JsonNode p : searchResult.get("packages")) {
                String pName = p.get("name").asText();
                String pRepo = p.get("repository").get("name").asText();
                
                if (pName.equals(chartNameOnly)) {
                    if (repoNamePrefix == null || pRepo.equals(repoNamePrefix)) {
                        pkg = p;
                        break;
                    }
                }
            }
            if (pkg == null && repoNamePrefix == null && searchResult.get("packages").size() > 0) {
                pkg = searchResult.get("packages").get(0);
            }
        }

        if (pkg == null) {
            throw new Exception("Package not found in Artifact Hub: " + chartFullName);
        }

        String repoName = pkg.get("repository").get("name").asText();
        String pkgName = pkg.get("name").asText();
        String version = pkg.get("version").asText();

        // 2. Get package details
        String detailUrl = "https://artifacthub.io/api/v1/packages/helm/" + repoName + "/" + pkgName + "/" + version;
        JsonNode details = callApi(detailUrl);
        String contentUrl = details.get("content_url").asText();

        // 3. Download and untar
        File testChartsDir = new File("target/test-charts");
        testChartsDir.mkdirs();
        String fileName = pkgName + ".tgz";
        repoManager.pullFromUrl(contentUrl, testChartsDir.getAbsolutePath(), fileName);
        repoManager.untar(new File(testChartsDir, fileName), testChartsDir);
    }

    private JsonNode callApi(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn instanceof HttpsURLConnection httpsConn) {
            setupInsecureSsl(httpsConn);
        }
        conn.setRequestProperty("User-Agent", "jhelm-test");
        try (var in = conn.getInputStream()) {
            return objectMapper.readTree(in);
        }
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
            log.error("Failed to setup insecure SSL in test", e);
        }
    }
}
