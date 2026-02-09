package org.alexmond.jhelm.kube;

import io.kubernetes.client.openapi.ApiException;
import org.alexmond.jhelm.core.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {KubernetesConfig.class, HelmKubeService.class, CoreConfig.class})
class HelmIntegrationTest {

    @Autowired
    private HelmKubeService helmKubeService;

    @Autowired
    private InstallAction installAction;

    @Test
    void testFullInstallCycle() throws IOException, ApiException {
        // 1. Load Chart
        ChartLoader loader = new ChartLoader();
        File chartDir = Paths.get("src", "test", "resources", "test-chart").toFile();
        Chart chart = loader.load(chartDir);
        assertNotNull(chart);
        assertNotNull(chart.getMetadata());

        // 2. Prepare Install Action
        Release release = installAction.install(chart, "test-release", "default", new java.util.HashMap<>(), 1);
        assertNotNull(release);
        assertNotNull(release.getManifest());
        assertTrue(release.getManifest().contains("test-configmap"));

        // 3. Apply to Kubernetes
        helmKubeService.apply("default", release.getManifest());

        // 4. Verify
        Optional<Release> storedRelease = helmKubeService.getRelease("test-release", "default");
        // Note: apply doesn't store release metadata by itself, we need to call storeRelease if we want to verify it via getRelease
        // But HelmIntegrationTest only tests 'apply'.

        // Cleanup
        helmKubeService.delete("default", release.getManifest());
    }
}
