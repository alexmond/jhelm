package org.alexmond.jhelm.kube;

import io.kubernetes.client.openapi.ApiException;
import org.alexmond.jhelm.core.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {KubernetesConfig.class, HelmKubeService.class, CoreConfig.class})
class HelmFullFlowIntegrationTest {

    @Autowired
    private HelmKubeService helmKubeService;

    @Autowired
    private InstallAction installAction;

    @Autowired
    private UpgradeAction upgradeAction;

    private final ChartLoader chartLoader = new ChartLoader();

    @Test
    void testFullFlow() throws IOException, ApiException {
        String releaseName = "test-nginx";
        String namespace = "default";

        // 1. Load Chart
        File chartDir = new File("../sample-charts/nginx");
        if (!chartDir.exists()) {
            chartDir = new File("sample-charts/nginx");
        }
        Chart chart = chartLoader.load(chartDir);
        assertNotNull(chart);

        // 2. Install
        Release release = installAction.install(chart, releaseName, namespace, Map.of("replicaCount", 2), 1);
        assertNotNull(release);
        assertTrue(release.getManifest().contains("replicas: 2"));
        assertTrue(release.getManifest().contains("test-nginx-nginx"));

        helmKubeService.apply(namespace, release.getManifest());
        helmKubeService.storeRelease(release);

        // 3. Verify Store
        Optional<Release> storedRelease = helmKubeService.getRelease(releaseName, namespace);
        assertTrue(storedRelease.isPresent());
        assertEquals(1, storedRelease.get().getVersion());

        // 4. Upgrade
        Release currentRelease = storedRelease.get();
        Release upgradedRelease = upgradeAction.upgrade(currentRelease, chart, Map.of("replicaCount", 3));
        assertEquals(2, upgradedRelease.getVersion());
        assertTrue(upgradedRelease.getManifest().contains("replicas: 3"));

        helmKubeService.apply(namespace, upgradedRelease.getManifest());
        helmKubeService.storeRelease(upgradedRelease);

        // 5. Verify Upgrade
        storedRelease = helmKubeService.getRelease(releaseName, namespace);
        assertTrue(storedRelease.isPresent());
        assertEquals(2, storedRelease.get().getVersion());

        // 6. Uninstall/Cleanup
        helmKubeService.delete(namespace, upgradedRelease.getManifest());
        helmKubeService.deleteReleaseHistory(releaseName, namespace);

        storedRelease = helmKubeService.getRelease(releaseName, namespace);
        assertFalse(storedRelease.isPresent());
    }
}
