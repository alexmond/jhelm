package org.alexmond.jhelm.core.service;

import java.io.File;
import java.util.Map;

import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.Release;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for dependency condition evaluation and alias propagation using a
 * dedicated test chart loaded from disk. Covers the datadog-style pattern where subcharts
 * are controlled by condition paths in requirements.yaml and renamed via alias. Exercises
 * the full ChartLoader + Engine pipeline (issue #202).
 */
class DependencyConditionTest {

	private final ChartLoader chartLoader = new ChartLoader();

	private final Engine engine = new Engine();

	private final InstallAction installAction = new InstallAction(engine, null);

	private Chart loadChart() throws Exception {
		File chartDir = new File("src/test/resources/test-charts/dependency-conditions");
		Chart chart = chartLoader.load(chartDir);
		assertNotNull(chart, "Chart should be loaded");
		return chart;
	}

	private String renderManifest(Chart chart, Map<String, Object> overrides) throws Exception {
		Release release = installAction.install(chart, "test-release", "default", overrides, 1, true);
		assertNotNull(release, "Release should be created");
		String manifest = release.getManifest();
		assertNotNull(manifest, "Manifest should not be null");
		return manifest;
	}

	@Test
	void testBackendRendersWithAliasName() throws Exception {
		Chart chart = loadChart();
		String manifest = renderManifest(chart, Map.of());

		// Backend subchart has alias "api" in requirements.yaml, so .Chart.Name = "api"
		assertTrue(manifest.contains("name: api-svc"),
				"Backend subchart should render with alias name 'api': " + manifest);
	}

	@Test
	void testMonitoringSubchartSkipped() throws Exception {
		Chart chart = loadChart();
		String manifest = renderManifest(chart, Map.of());

		// Monitoring subchart condition is app.monitoring.enabled=false, should be
		// skipped
		assertFalse(manifest.contains("monitoring-svc"),
				"Monitoring subchart should not render when condition is false: " + manifest);
	}

	@Test
	void testParentConfigMapRendered() throws Exception {
		Chart chart = loadChart();
		String manifest = renderManifest(chart, Map.of());

		// Parent template should render normally
		assertTrue(manifest.contains("name: test-release-config"), "Parent ConfigMap should render: " + manifest);
		assertTrue(manifest.contains("appName: dependency-conditions-app"),
				"Parent ConfigMap should contain values: " + manifest);
	}

	@Test
	void testBackendServicePort() throws Exception {
		Chart chart = loadChart();
		String manifest = renderManifest(chart, Map.of());

		// Backend subchart default port should be rendered
		assertTrue(manifest.contains("port: 8080"), "Backend service should use its default port: " + manifest);
	}

}
