package org.alexmond.jhelm.core;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test subchart value propagation patterns using the dedicated test chart. Verifies that
 * parent chart templates can access deep subchart default values — the pattern used by
 * gitea, airflow, and other complex charts. Covers:
 * <ul>
 * <li>Direct access: {@code index .Values "database" "service" "port"} reads subchart
 * defaults</li>
 * <li>Computed helpers: _helpers.tpl builds connection strings from subchart values</li>
 * <li>Override merging: parent values override subchart defaults</li>
 * <li>Global propagation: global.env is accessible in both parent and subchart
 * contexts</li>
 * </ul>
 */
class SubchartValuePropagationTest {

	private final ChartLoader chartLoader = new ChartLoader();

	private final Engine engine = new Engine();

	private final InstallAction installAction = new InstallAction(engine, null);

	private Chart loadChart() throws Exception {
		File chartDir = new File("src/test/resources/test-charts/subchart-value-propagation");
		Chart chart = chartLoader.load(chartDir);
		assertNotNull(chart, "Chart should be loaded");
		return chart;
	}

	private String renderManifest(Chart chart, Map<String, Object> overrides) throws Exception {
		Release release = installAction.install(chart, "test-release", "default", overrides, 1, true);
		assertNotNull(release, "Release should be created");
		String manifest = release.getManifest();
		assertNotNull(manifest, "Manifest should not be null");
		assertFalse(manifest.trim().isEmpty(), "Manifest should not be empty");
		return manifest;
	}

	@Test
	void testDirectSubchartValueAccess() throws Exception {
		Chart chart = loadChart();
		String manifest = renderManifest(chart, Map.of());

		// Parent template accesses database subchart defaults via index .Values
		assertTrue(manifest.contains("db-host: localhost"),
				"Parent should access database.service.host default: " + manifest);
		assertTrue(manifest.contains("db-port: \"5432\""),
				"Parent should access database.service.port default: " + manifest);
		assertTrue(manifest.contains("cache-port: \"6379\""),
				"Parent should access cache.service.port default: " + manifest);
	}

	@Test
	void testComputedHelpersFromSubchartValues() throws Exception {
		Chart chart = loadChart();
		String manifest = renderManifest(chart, Map.of());

		// _helpers.tpl builds connection strings using subchart port values
		assertTrue(manifest.contains("database-url: test-release-database.default.svc.cluster.local:5432"),
				"Database connection string should use subchart port: " + manifest);
		assertTrue(manifest.contains("cache-url: test-release-cache.default.svc.cluster.local:6379"),
				"Cache connection string should use subchart port: " + manifest);
	}

	@Test
	void testOverrideMergingWithSubchartDefaults() throws Exception {
		Chart chart = loadChart();
		String manifest = renderManifest(chart, Map.of());

		// Parent values.yaml overrides database.auth.password from "default-password"
		// to "secret123"
		assertTrue(manifest.contains("db-password: secret123"),
				"Parent override should take precedence over subchart default: " + manifest);
	}

	@Test
	void testParentNullOverrideDeletesSubchartDefault() throws Exception {
		// Helm's coalesce deletes a key whose override is null, even against the
		// subchart's
		// OWN default. The database subchart renders `port: {{ .Values.service.port }}`
		// from its default 5432; a null override on database.service.port must delete it,
		// not fall back to 5432. (Regression: jhelm pruned the null at the parent level
		// before the subchart re-merged its default, re-introducing 5432.)
		Chart chart = loadChart();
		Map<String, Object> service = new HashMap<>();
		service.put("port", null);
		Map<String, Object> overrides = Map.of("database", Map.of("service", service));
		String manifest = renderManifest(chart, overrides);
		assertFalse(manifest.contains("port: 5432"),
				"null override must delete the subchart's default port, not fall back to 5432: " + manifest);
	}

	@Test
	void testGlobalValuePropagation() throws Exception {
		Chart chart = loadChart();
		String manifest = renderManifest(chart, Map.of());

		// global.env: production should be accessible in parent template
		assertTrue(manifest.contains("environment: production"),
				"Global values should be accessible in parent template: " + manifest);
	}

	@Test
	void testUserOverridesSubchartValues() throws Exception {
		Chart chart = loadChart();
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("database",
				new HashMap<>(Map.of("auth", new HashMap<>(Map.of("password", "user-override-pwd")))));

		String manifest = renderManifest(chart, overrides);

		// User-provided override should win over parent values.yaml
		assertTrue(manifest.contains("db-password: user-override-pwd"),
				"User override should take precedence: " + manifest);
	}

	@Test
	void testSubchartTemplatesRendered() throws Exception {
		Chart chart = loadChart();
		String manifest = renderManifest(chart, Map.of());

		// Subchart Service templates should also be rendered
		assertTrue(manifest.contains("name: test-release-database"),
				"Database subchart service should be rendered: " + manifest);
		assertTrue(manifest.contains("name: test-release-cache"),
				"Cache subchart service should be rendered: " + manifest);
	}

	@Test
	void testSubchartServiceUsesOwnDefaults() throws Exception {
		Chart chart = loadChart();
		String manifest = renderManifest(chart, Map.of());

		// Subchart templates see their own scoped values
		assertTrue(manifest.contains("port: 5432"), "Database service should use its default port: " + manifest);
		assertTrue(manifest.contains("port: 6379"), "Cache service should use its default port: " + manifest);
	}

}
