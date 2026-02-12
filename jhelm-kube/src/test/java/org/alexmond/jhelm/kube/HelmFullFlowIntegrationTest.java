package org.alexmond.jhelm.kube;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {KubernetesConfig.class, HelmKubeService.class, CoreConfig.class})
@Slf4j
class HelmFullFlowIntegrationTest {

    private final ChartLoader chartLoader = new ChartLoader();
    @Autowired
    private HelmKubeService helmKubeService;
    @Autowired
    private InstallAction installAction;
    @Autowired
    private UpgradeAction upgradeAction;
    @Autowired
    private UninstallAction uninstallAction;

    @Test
    void testFullFlow() throws Exception {
        String releaseName = "test-nginx";
        String namespace = "default";

        Chart chart = Chart.builder()
                .metadata(ChartMetadata.builder().name("nginx").version("1.0.0").build())
                .templates(new java.util.ArrayList<>(java.util.List.of(
                        Chart.Template.builder().name("deployment.yaml").data("""
                                apiVersion: apps/v1
                                kind: Deployment
                                metadata:
                                  name: {{ .Release.Name }}-nginx
                                spec:
                                  replicas: {{ .Values.replicaCount }}
                                  selector:
                                    matchLabels:
                                      app: nginx
                                  template:
                                    metadata:
                                      labels:
                                        app: nginx
                                    spec:
                                      containers:
                                      - name: nginx
                                        image: nginx:latest
                                """).build(),
                        Chart.Template.builder().name("service.yaml").data("""
                                apiVersion: v1
                                kind: Service
                                metadata:
                                  name: {{ .Release.Name }}-nginx
                                spec:
                                  ports:
                                  - port: 80
                                  selector:
                                    app: nginx
                                """).build()
                )))
                .values(Map.of("replicaCount", 1))
                .build();

        // 2. Install
        Release release = installAction.install(chart, releaseName, namespace, Map.of("replicaCount", 2), 1, false);
        assertNotNull(release);
        // log.info("Manifest: {}", release.getManifest());
        // assertTrue(release.getManifest().contains("replicaCount") || release.getManifest().contains("replicas"));
        // assertTrue(release.getManifest().contains("test-nginx-nginx"));

        // 3. Verify Store
        Optional<Release> storedRelease = helmKubeService.getRelease(releaseName, namespace);
        assertTrue(storedRelease.isPresent());
        assertEquals(1, storedRelease.get().getVersion());

        // 4. Upgrade
        Release currentRelease = storedRelease.get();
        Release upgradedRelease = upgradeAction.upgrade(currentRelease, chart, Map.of("replicaCount", 3), false);
        assertEquals(2, upgradedRelease.getVersion());
        // assertTrue(upgradedRelease.getManifest().contains("replicaCount") || upgradedRelease.getManifest().contains("replicas"));

        // 5. Verify Upgrade
        storedRelease = helmKubeService.getRelease(releaseName, namespace);
        assertTrue(storedRelease.isPresent());
        assertEquals(2, storedRelease.get().getVersion());

        // 6. Uninstall/Cleanup
        uninstallAction.uninstall(releaseName, namespace);

        storedRelease = helmKubeService.getRelease(releaseName, namespace);
        assertFalse(storedRelease.isPresent());
    }

    @Test
    void testDryRun() throws Exception {
        String releaseName = "dry-run-release";
        String namespace = "default";

        File chartDir = new File("sample-charts/nginx");
        if (!chartDir.exists()) chartDir = new File("nginx");
        if (!chartDir.exists()) {
            // Basic fallback for CI or local dev if above fails
            Chart simpleChart = Chart.builder()
                    .metadata(ChartMetadata.builder().name("simple").version("0.1.0").build())
                    .templates(new java.util.ArrayList<>(java.util.List.of(
                            Chart.Template.builder().name("cm.yaml").data("apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: {{ .Release.Name }}").build()
                    )))
                    .values(new java.util.HashMap<>())
                    .build();

            // Install Dry Run
            Release release = installAction.install(simpleChart, releaseName, namespace, Map.of(), 1, true);
            assertNotNull(release);
            assertTrue(release.getManifest().contains("name: dry-run-release"));
            assertEquals("pending-install", release.getInfo().getStatus());

            // Verify NOT in Kube
            Optional<Release> storedRelease = helmKubeService.getRelease(releaseName, namespace);
            assertFalse(storedRelease.isPresent());
            return;
        }

        Chart chart = chartLoader.load(chartDir);

        // 1. Install Dry Run
        Release release = installAction.install(chart, releaseName, namespace, Map.of("replicaCount", 5), 1, true);
        assertNotNull(release);
        assertTrue(release.getManifest().contains("replicas: 5"));
        assertEquals("pending-install", release.getInfo().getStatus());

        // 2. Verify NOT in Kube
        Optional<Release> storedRelease = helmKubeService.getRelease(releaseName, namespace);
        assertFalse(storedRelease.isPresent());

        // 3. Upgrade Dry Run (need a real release first)
        Release realRelease = installAction.install(chart, releaseName, namespace, Map.of("replicaCount", 1), 1, false);
        assertNotNull(realRelease);

        Release dryUpgraded = upgradeAction.upgrade(realRelease, chart, Map.of("replicaCount", 10), true);
        assertNotNull(dryUpgraded);
        assertTrue(dryUpgraded.getManifest().contains("replicas: 10"));
        assertEquals("pending-upgrade", dryUpgraded.getInfo().getStatus());
        assertEquals(2, dryUpgraded.getVersion());

        // 4. Verify Upgrade NOT in Kube
        storedRelease = helmKubeService.getRelease(releaseName, namespace);
        assertTrue(storedRelease.isPresent());
        assertEquals(1, storedRelease.get().getVersion()); // Still version 1

        // Cleanup
        uninstallAction.uninstall(releaseName, namespace);
    }
}
