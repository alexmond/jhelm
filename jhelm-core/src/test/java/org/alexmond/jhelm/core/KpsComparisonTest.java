package org.alexmond.jhelm.core;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.Map;

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
        
        Release release = installAction.install(chart, "simple", "default", Map.of(), 1);
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
            Release release = installAction.install(chart, releaseName, "default", Map.of(), 1);
            assertNotNull(release);
            
            log.info("{} - Manifest length: {}", chartName, release.getManifest().length());
            if (release.getManifest().length() == 0) {
                log.info("{} - Values: {}", chartName, chart.getValues().keySet());
            }
            
            File actualFile = new File("target/test-output/actual_" + chartName + ".yaml");
            actualFile.getParentFile().mkdirs();
            Files.writeString(actualFile.toPath(), release.getManifest());
            log.info("{} - Actual manifest written to: {}", chartName, actualFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            fail(chartName + " - Rendering failed: " + e.getMessage());
        }
    }

    private File findChartDir(String chartName) {
        String[] paths = {
            "sample-charts/" + chartName,
            "../sample-charts/" + chartName,
            "target/test-charts/" + chartName,
            "target/test-charts/bitnami/" + chartName, // common subfolder in bitnami tgz
            "nginx", // legacy check
            "../nginx"
        };
        for (String p : paths) {
            File f = new File(p);
            if (f.exists() && f.isDirectory() && new File(f, "Chart.yaml").exists()) {
                return f;
            }
        }
        return null;
    }

    private void fetchFromArtifactHub(String chartName) throws Exception {
        // 1. Search for package
        String searchUrl = "https://artifacthub.io/api/v1/packages/search?ts_query_web=" + chartName + "&kind=0";
        JsonNode searchResult = callApi(searchUrl);
        JsonNode pkg = null;
        if (searchResult.has("packages") && searchResult.get("packages").isArray()) {
            for (JsonNode p : searchResult.get("packages")) {
                if (p.get("name").asText().equals(chartName)) {
                    pkg = p;
                    break;
                }
            }
            if (pkg == null && searchResult.get("packages").size() > 0) {
                pkg = searchResult.get("packages").get(0);
            }
        }

        if (pkg == null) {
            throw new Exception("Package not found in Artifact Hub: " + chartName);
        }

        String repoName = pkg.get("repository").get("name").asText();
        String pkgName = pkg.get("name").asText();

        // 2. Get package details
        String detailUrl = "https://artifacthub.io/api/v1/packages/helm/" + repoName + "/" + pkgName;
        JsonNode details = callApi(detailUrl);
        String contentUrl = details.get("content_url").asText();

        if (contentUrl.startsWith("oci://")) {
            throw new Exception("OCI charts not supported yet: " + contentUrl);
        }

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
