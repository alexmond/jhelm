package org.alexmond.jhelm.kube.service;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.action.UpgradeOptions;
import org.alexmond.jhelm.core.action.UpgradeValueStrategy;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
import org.alexmond.jhelm.core.CoreConfig;
import org.alexmond.jhelm.kube.KubernetesConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SpringBootTest(classes = { KubernetesConfig.class, HelmKubeService.class, CoreConfig.class })
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
			.templates(new ArrayList<>(List.of(Chart.Template.builder().name("deployment.yaml").data("""
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
					""").build(), Chart.Template.builder().name("service.yaml").data("""
					apiVersion: v1
					kind: Service
					metadata:
					  name: {{ .Release.Name }}-nginx
					spec:
					  ports:
					  - port: 80
					  selector:
					    app: nginx
					""").build())))
			.values(Map.of("replicaCount", 1))
			.build();

		// 2. Install
		Release release = installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName(releaseName)
			.namespace(namespace)
			.values(Map.of("replicaCount", 2))
			.revision(1)
			.dryRun(false)
			.build());
		assertNotNull(release);
		// log.info("Manifest: {}", release.getManifest());
		// assertTrue(release.getManifest().contains("replicaCount") ||
		// release.getManifest().contains("replicas"));
		// assertTrue(release.getManifest().contains("test-nginx-nginx"));

		// 3. Verify Store
		Optional<Release> storedRelease = helmKubeService.getRelease(releaseName, namespace);
		assertTrue(storedRelease.isPresent());
		assertEquals(1, storedRelease.get().getVersion());

		// 4. Upgrade
		Release currentRelease = storedRelease.get();
		Release upgradedRelease = upgradeAction.upgrade(UpgradeOptions.builder()
			.currentRelease(currentRelease)
			.newChart(chart)
			.values(Map.of("replicaCount", 3))
			.valueStrategy(UpgradeValueStrategy.DEFAULT)
			.dryRun(false)
			.build());
		assertEquals(2, upgradedRelease.getVersion());
		// assertTrue(upgradedRelease.getManifest().contains("replicaCount") ||
		// upgradedRelease.getManifest().contains("replicas"));

		// 5. Verify Upgrade
		storedRelease = helmKubeService.getRelease(releaseName, namespace);
		assertTrue(storedRelease.isPresent());
		assertEquals(2, storedRelease.get().getVersion());

		// 6. Uninstall/Cleanup
		uninstallAction.uninstall(UninstallOptions.builder().releaseName(releaseName).namespace(namespace).build());

		storedRelease = helmKubeService.getRelease(releaseName, namespace);
		assertFalse(storedRelease.isPresent());
	}

	@Test
	void testDryRun() throws Exception {
		String releaseName = "dry-run-release";
		String namespace = "default";

		File chartDir = new File("sample-charts/nginx");
		if (!chartDir.exists()) {
			chartDir = new File("nginx");
		}
		if (!chartDir.exists()) {
			// Basic fallback for CI or local dev if above fails
			Chart simpleChart = Chart.builder()
				.metadata(ChartMetadata.builder().name("simple").version("0.1.0").build())
				.templates(new ArrayList<>(List.of(Chart.Template.builder()
					.name("cm.yaml")
					.data("apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: {{ .Release.Name }}")
					.build())))
				.values(new HashMap<>())
				.build();

			// Install Dry Run
			Release release = installAction.install(InstallOptions.builder()
				.chart(simpleChart)
				.releaseName(releaseName)
				.namespace(namespace)
				.values(Map.of())
				.revision(1)
				.dryRun(true)
				.build());
			assertNotNull(release);
			assertTrue(release.getManifest().contains("name: dry-run-release"));
			assertEquals(ReleaseStatus.PENDING_INSTALL, release.getInfo().getStatus());

			// Verify NOT in Kube
			Optional<Release> storedRelease = helmKubeService.getRelease(releaseName, namespace);
			assertFalse(storedRelease.isPresent());
			return;
		}

		Chart chart = chartLoader.load(chartDir);

		// 1. Install Dry Run
		Release release = installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName(releaseName)
			.namespace(namespace)
			.values(Map.of("replicaCount", 5))
			.revision(1)
			.dryRun(true)
			.build());
		assertNotNull(release);
		assertTrue(release.getManifest().contains("replicas: 5"));
		assertEquals(ReleaseStatus.PENDING_INSTALL, release.getInfo().getStatus());

		// 2. Verify NOT in Kube
		Optional<Release> storedRelease = helmKubeService.getRelease(releaseName, namespace);
		assertFalse(storedRelease.isPresent());

		// 3. Upgrade Dry Run (need a real release first)
		Release realRelease = installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName(releaseName)
			.namespace(namespace)
			.values(Map.of("replicaCount", 1))
			.revision(1)
			.dryRun(false)
			.build());
		assertNotNull(realRelease);

		Release dryUpgraded = upgradeAction.upgrade(UpgradeOptions.builder()
			.currentRelease(realRelease)
			.newChart(chart)
			.values(Map.of("replicaCount", 10))
			.valueStrategy(UpgradeValueStrategy.DEFAULT)
			.dryRun(true)
			.build());
		assertNotNull(dryUpgraded);
		assertTrue(dryUpgraded.getManifest().contains("replicas: 10"));
		assertEquals(ReleaseStatus.PENDING_UPGRADE, dryUpgraded.getInfo().getStatus());
		assertEquals(2, dryUpgraded.getVersion());

		// 4. Verify Upgrade NOT in Kube
		storedRelease = helmKubeService.getRelease(releaseName, namespace);
		assertTrue(storedRelease.isPresent());
		assertEquals(1, storedRelease.get().getVersion()); // Still version 1

		// Cleanup
		uninstallAction.uninstall(UninstallOptions.builder().releaseName(releaseName).namespace(namespace).build());
	}

}
