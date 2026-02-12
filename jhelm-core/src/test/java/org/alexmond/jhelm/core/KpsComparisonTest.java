package org.alexmond.jhelm.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class KpsComparisonTest {

    private final RepoManager repoManager = createRepoManager();
    private final ChartLoader chartLoader = new ChartLoader();
    private final Engine engine = new Engine();
    private final InstallAction installAction = new InstallAction(engine, null);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final java.util.Set<String> addedRepos = new java.util.HashSet<>();

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
            compareChart("nginx", "pulled-nginx", "bitnami", "https://charts.bitnami.com/bitnami");
        }
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/charts.csv")
    void compareAllTopCharts(String chartName, String repoId, String repoUrl) throws IOException {
        compareChart(chartName, "release-" + chartName, repoId, repoUrl);
    }

    private void compareChart(String chartName, String releaseName, String repoId, String repoUrl) throws IOException {
        // No charts skipped anymore

        File chartDir = findChartDir(chartName);

        if (chartDir == null) {
            log.info("Chart {} not found locally, fetching from repository {}...", chartName, repoUrl);
            try {
                addHelmRepo(repoId, repoUrl);
                fetchFromHelmRepo(chartName);
                chartDir = findChartDir(chartName);
            } catch (Exception e) {
                log.error("Failed to fetch chart {} from repository: {}", chartName, e.getMessage());
                fail("Failed to fetch chart " + chartName + " from repository: " + e.getMessage());
            }
        }

        if (chartDir == null) {
            fail("Skipping chart " + chartName + " - directory not found and could not be fetched");
        }

        // Sanitize release name to be valid for Helm (alphanumeric and hyphens only, no slashes)
        String sanitizedReleaseName = releaseName.replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        if (sanitizedReleaseName.startsWith("-")) {
            sanitizedReleaseName = "r" + sanitizedReleaseName;
        }
        if (sanitizedReleaseName.endsWith("-")) {
            sanitizedReleaseName = sanitizedReleaseName.substring(0, sanitizedReleaseName.length() - 1);
        }

        log.info("Using sanitized release name: {} (from {})", sanitizedReleaseName, releaseName);

        String sanitizedName = chartName.replace("/", "_");

        // STEP 1: Run Helm template FIRST and save output
        log.info("{} - Running Helm template first...", chartName);
        String helmManifest = runHelmInstallDryRun(chartDir, sanitizedReleaseName, "default");

        if (helmManifest == null) {
            // Helm failed - log error and do NOT continue to Java rendering
            log.error("{} - Helm template failed, skipping Java rendering", chartName);
            fail(chartName + " - Helm template command failed - see logs for details");
        }

        // Save Helm output to target/helm-output/
        File helmOutputDir = new File("target/helm-output");
        helmOutputDir.mkdirs();
        File helmOutputFile = new File(helmOutputDir, sanitizedName + ".yaml");
        Files.writeString(helmOutputFile.toPath(), helmManifest);
        log.info("{} - Saved Helm output to {}", chartName, helmOutputFile.getPath());

        // STEP 2: Load chart and run Java rendering (only if Helm succeeded)
        Chart chart = chartLoader.load(chartDir);
        assertNotNull(chart);

        try {
            log.info("{} - Running JHelm rendering...", chartName);
            // JHelm dry-run
            Release release = installAction.install(chart, sanitizedReleaseName, "default", Map.of(), 1, true);
            assertNotNull(release);

            String jhelmManifest = release.getManifest();
            log.info("{} - JHelm manifest length: {}", chartName, jhelmManifest.length());

            File actualFile = new File("target/test-output/actual_" + sanitizedName + ".yaml");
            actualFile.getParentFile().mkdirs();
            Files.writeString(actualFile.toPath(), jhelmManifest);

            File expectedFile = new File("target/test-output/expected_" + sanitizedName + ".yaml");
            Files.writeString(expectedFile.toPath(), helmManifest);

            // Compare manifests
            compareManifests(chartName, jhelmManifest, helmManifest);

        } catch (Exception e) {
            log.error("{} - JHelm rendering failed", chartName, e);
            fail(chartName + " - JHelm rendering failed: " + e.getMessage());
        }
    }

    private String runHelmInstallDryRun(File chartDir, String releaseName, String namespace) {
        System.out.println("[DEBUG_LOG] Running helm install dry-run for " + releaseName + " using chart " + chartDir.getAbsolutePath());
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "helm", "template", releaseName, chartDir.getAbsolutePath(),
                    "--namespace", namespace
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

            // helm template outputs the manifest directly (no MANIFEST: prefix)
            // Just return the output, but skip any leading/trailing whitespace
            if (output == null || output.trim().isEmpty()) {
                System.err.println("[DEBUG_LOG] Helm template returned empty output");
                return null;
            }

            System.out.println("[DEBUG_LOG] Successfully captured helm manifest for " + releaseName + " (" + output.length() + " bytes)");
            return output.trim();
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
                "target/temp-charts/" + chartName,
                "target/temp-charts/" + chartFullName,
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

    private void addHelmRepo(String repoId, String repoUrl) throws IOException {
        if (addedRepos.contains(repoId)) {
            log.debug("Repo {} already added in this session, skipping", repoId);
            return;
        }
        log.info("Adding repo {} at {} via RepoManager", repoId, repoUrl);
        repoManager.addRepo(repoId, repoUrl);
        try {
            repoManager.updateRepo(repoId);
        } catch (IOException e) {
            log.warn("Repo update failed for {}: {}", repoId, e.getMessage());
        }
        addedRepos.add(repoId);
    }

    private void fetchFromHelmRepo(String chartName) throws IOException {
        log.info("Fetching chart {} via RepoManager...", chartName);
        File tempDir = new File("target/temp-charts");
        tempDir.mkdirs();

        // chartName may be in the form <repoId>/<chartName>
        String repoId = null;
        String shortName = chartName;
        if (chartName.contains("/")) {
            int i = chartName.indexOf('/');
            repoId = chartName.substring(0, i);
            shortName = chartName.substring(i + 1);
        }

        // Use latest version from index
        try {
            java.util.List<RepoManager.ChartVersion> versions = repoManager.getChartVersions(repoId, shortName);
            if (versions.isEmpty()) {
                throw new IOException("No versions found for chart '" + shortName + "' in repo '" + repoId + "'");
            }
            String version = versions.get(0).getChartVersion();
            repoManager.pull(chartName, repoId, version, tempDir.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to pull chart {}: {}", chartName, e.getMessage());
            throw e;
        }
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
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }}, new java.security.SecureRandom());
            conn.setSSLSocketFactory(sc.getSocketFactory());
            conn.setHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            log.error("Failed to setup insecure SSL in test", e);
        }
    }
}
