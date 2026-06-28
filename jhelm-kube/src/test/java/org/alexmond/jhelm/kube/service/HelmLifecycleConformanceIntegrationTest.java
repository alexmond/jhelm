package org.alexmond.jhelm.kube.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.CoreConfig;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.RollbackOptions;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.action.UpgradeOptions;
import org.alexmond.jhelm.core.action.UpgradeValueStrategy;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.model.ReleaseStatus;
import org.alexmond.jhelm.kube.KubeClusterAvailable;
import org.alexmond.jhelm.kube.KubernetesConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end lifecycle conformance against a live cluster (kind in CI): drives the full
 * Helm day-2 flow install → upgrade → rollback → uninstall through the real action layer
 * and asserts the resulting revision numbers and release statuses, plus the
 * {@code --keep-history} uninstall variant. Skipped automatically when no cluster is
 * reachable (see {@link KubeClusterAvailable}).
 */
@SpringBootTest(classes = { KubernetesConfig.class, HelmKubeService.class, CoreConfig.class })
@KubeClusterAvailable
@Slf4j
class HelmLifecycleConformanceIntegrationTest {

	private static final String NAMESPACE = "default";

	@Autowired
	private HelmKubeService helmKubeService;

	@Autowired
	private InstallAction installAction;

	@Autowired
	private UpgradeAction upgradeAction;

	@Autowired
	private RollbackAction rollbackAction;

	@Autowired
	private UninstallAction uninstallAction;

	@Test
	void testInstallUpgradeRollbackUninstall() {
		String releaseName = "conformance-iur";
		Chart chart = nginxChart();
		try {
			// Install — revision 1, deployed.
			Release installed = installAction.install(InstallOptions.builder()
				.chart(chart)
				.releaseName(releaseName)
				.namespace(NAMESPACE)
				.values(Map.of("replicaCount", 1))
				.revision(1)
				.dryRun(false)
				.build());
			assertNotNull(installed);
			assertReleaseAt(releaseName, 1, ReleaseStatus.DEPLOYED);

			// Upgrade — revision 2, deployed.
			Release current = helmKubeService.getRelease(releaseName, NAMESPACE).orElseThrow();
			Release upgraded = upgradeAction.upgrade(UpgradeOptions.builder()
				.currentRelease(current)
				.newChart(chart)
				.values(Map.of("replicaCount", 2))
				.valueStrategy(UpgradeValueStrategy.DEFAULT)
				.dryRun(false)
				.build());
			assertEquals(2, upgraded.getVersion());
			assertReleaseAt(releaseName, 2, ReleaseStatus.DEPLOYED);

			// Rollback to revision 1 — produces a new revision 3, deployed.
			Release rolledBack = rollbackAction
				.rollback(RollbackOptions.builder().releaseName(releaseName).namespace(NAMESPACE).revision(1).build());
			assertNotNull(rolledBack);
			assertEquals(3, rolledBack.getVersion());
			assertEquals(ReleaseStatus.DEPLOYED, rolledBack.getInfo().getStatus());
			assertReleaseAt(releaseName, 3, ReleaseStatus.DEPLOYED);

			// History retains all three revisions.
			List<Integer> versions = helmKubeService.getReleaseHistory(releaseName, NAMESPACE)
				.stream()
				.map(Release::getVersion)
				.toList();
			assertTrue(versions.containsAll(List.of(1, 2, 3)), "history versions: " + versions);

			// Uninstall (default) — release record is removed.
			uninstallAction.uninstall(UninstallOptions.builder().releaseName(releaseName).namespace(NAMESPACE).build());
			assertFalse(helmKubeService.getRelease(releaseName, NAMESPACE).isPresent());
		}
		finally {
			cleanup(releaseName, chart);
		}
	}

	@Test
	void testUninstallKeepHistory() {
		String releaseName = "conformance-keep";
		Chart chart = nginxChart();
		try {
			installAction.install(InstallOptions.builder()
				.chart(chart)
				.releaseName(releaseName)
				.namespace(NAMESPACE)
				.values(Map.of("replicaCount", 1))
				.revision(1)
				.dryRun(false)
				.build());
			assertReleaseAt(releaseName, 1, ReleaseStatus.DEPLOYED);

			// --keep-history retains the record and flips its status to uninstalled.
			uninstallAction.uninstall(
					UninstallOptions.builder().releaseName(releaseName).namespace(NAMESPACE).keepHistory(true).build());
			Optional<Release> kept = helmKubeService.getRelease(releaseName, NAMESPACE);
			assertTrue(kept.isPresent(), "release history should be retained");
			assertEquals(ReleaseStatus.UNINSTALLED, kept.get().getInfo().getStatus());
		}
		finally {
			cleanup(releaseName, chart);
		}
	}

	private void assertReleaseAt(String releaseName, int version, ReleaseStatus status) {
		Release stored = helmKubeService.getRelease(releaseName, NAMESPACE).orElseThrow();
		assertEquals(version, stored.getVersion());
		assertEquals(status, stored.getInfo().getStatus());
	}

	/**
	 * Best-effort teardown so a failed assertion never leaks cluster state into the next
	 * run.
	 */
	private void cleanup(String releaseName, Chart chart) {
		try {
			helmKubeService.deleteReleaseHistory(releaseName, NAMESPACE);
		}
		catch (Exception ex) {
			log.debug("cleanup deleteReleaseHistory({}) ignored: {}", releaseName, ex.getMessage());
		}
	}

	private Chart nginxChart() {
		return Chart.builder()
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
					""").build(), Chart.Template.builder().name("configmap.yaml").data("""
					apiVersion: v1
					kind: ConfigMap
					metadata:
					  name: {{ .Release.Name }}-nginx
					data:
					  replicas: "{{ .Values.replicaCount }}"
					""").build())))
			.values(Map.of("replicaCount", 1))
			.build();
	}

}
