package org.alexmond.jhelm.core.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.exception.TemplateRenderException;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {

	private Engine engine;

	@BeforeEach
	void setUp() {
		engine = new Engine();
	}

	private Chart.Template tmpl(String name, String data) {
		return Chart.Template.builder().name(name).data(data).build();
	}

	private Chart simpleChart(String name, String version, List<Chart.Template> templates, Map<String, Object> values) {
		return Chart.builder()
			.metadata(ChartMetadata.builder().name(name).version(version).build())
			.templates(templates)
			.values(values)
			.build();
	}

	private Map<String, Object> releaseInfo() {
		Map<String, Object> info = new HashMap<>();
		info.put("Name", "test-release");
		info.put("Namespace", "default");
		info.put("IsInstall", true);
		info.put("IsUpgrade", false);
		info.put("Revision", 1);
		return info;
	}

	// --- basic rendering ---

	@Test
	void testRenderSimpleTemplate() {
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("configmap.yaml", "apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: test")), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("kind: ConfigMap"));
		assertTrue(result.contains("name: test"));
	}

	@Test
	void testRenderTemplateWithValues() {
		Chart chart = simpleChart("mychart", "1.0.0", List.of(tmpl("configmap.yaml", "name: {{ .Values.appName }}")),
				Map.of("appName", "myapp-default"));

		String result = engine.render(chart, Map.of("appName", "myapp"), releaseInfo());
		assertTrue(result.contains("name: myapp"));
	}

	@Test
	void testRenderUsesDefaultValues() {
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("configmap.yaml", "replicas: {{ .Values.replicas }}")), Map.of("replicas", 3));

		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("replicas: 3"));
	}

	@Test
	void testRenderOverridesDefaultValues() {
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("configmap.yaml", "replicas: {{ .Values.replicas }}")), Map.of("replicas", 3));

		String result = engine.render(chart, Map.of("replicas", 5), releaseInfo());
		assertTrue(result.contains("replicas: 5"));
	}

	// --- Release info ---

	@Test
	void testRenderReleaseInfo() {
		Chart chart = simpleChart("mychart", "1.0.0", List.of(tmpl("notes.yaml", "release: {{ .Release.Name }}")),
				Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("release: test-release"));
	}

	// --- Chart metadata ---

	@Test
	void testRenderChartMetadata() {
		Chart chart = simpleChart("mychart", "2.0.0",
				List.of(tmpl("labels.yaml", "chart: {{ .Chart.name }}-{{ .Chart.version }}")), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("chart: mychart-2.0.0"));
	}

	// --- Named templates (define/include) ---

	@Test
	void testRenderWithNamedTemplates() {
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("_helpers.tpl", "{{ define \"mychart.name\" }}mychart{{ end }}"),
						tmpl("configmap.yaml", "name: {{ include \"mychart.name\" . }}\nkind: ConfigMap")),
				Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("name: mychart"));
	}

	// --- Non-yaml templates are skipped ---

	@Test
	void testNonYamlTemplatesAreSkipped() {
		Chart chart = simpleChart("mychart", "1.0.0", List
			.of(tmpl("_helpers.tpl", "{{ define \"test\" }}helper{{ end }}"), tmpl("NOTES.txt", "This is notes")),
				Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertFalse(result.contains("This is notes"));
	}

	// --- Empty manifest cleanup ---

	@Test
	void testCleanManifestRemovesEmptyDocuments() {
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("configmap.yaml", "apiVersion: v1\nkind: ConfigMap")), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertNotNull(result);
		assertFalse(result.startsWith("---"));
	}

	// --- Values merging (deep merge) ---

	@Test
	void testDeepValuesMerge() {
		Map<String, Object> defaults = new HashMap<>();
		defaults.put("db", new HashMap<>(Map.of("host", "localhost", "port", 5432)));

		Map<String, Object> overrides = new HashMap<>();
		overrides.put("db", new HashMap<>(Map.of("host", "prod-db.example.com")));

		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("config.yaml", "host: {{ .Values.db.host }}\nport: {{ .Values.db.port }}")), defaults);
		String result = engine.render(chart, overrides, releaseInfo());
		assertTrue(result.contains("host: prod-db.example.com"));
		assertTrue(result.contains("port: 5432"));
	}

	// --- Subchart rendering ---

	@Test
	void testRenderWithSubchart() {
		Chart subchart = simpleChart("redis", "17.0.0", List.of(tmpl("service.yaml", "kind: Service\nname: redis")),
				Map.of());

		Chart parent = Chart.builder()
			.metadata(ChartMetadata.builder().name("parent").version("1.0.0").build())
			.templates(List.of(tmpl("deploy.yaml", "kind: Deployment\nname: parent-app")))
			.values(Map.of())
			.dependencies(List.of(subchart))
			.build();

		String result = engine.render(parent, Map.of(), releaseInfo());
		assertTrue(result.contains("name: parent-app"));
		assertTrue(result.contains("name: redis"));
	}

	@Test
	void testDisabledSubchartIsSkipped() {
		Chart subchart = simpleChart("redis", "17.0.0", List.of(tmpl("service.yaml", "kind: Service\nname: redis-svc")),
				Map.of());

		Chart parent = Chart.builder()
			.metadata(ChartMetadata.builder().name("parent").version("1.0.0").build())
			.templates(List.of(tmpl("deploy.yaml", "kind: Deployment\nname: parent-app")))
			.values(Map.of())
			.dependencies(List.of(subchart))
			.build();

		// Disable redis subchart via values
		Map<String, Object> vals = new HashMap<>();
		vals.put("redis", new HashMap<>(Map.of("enabled", false)));

		String result = engine.render(parent, vals, releaseInfo());
		assertTrue(result.contains("name: parent-app"));
		assertFalse(result.contains("name: redis-svc"));
	}

	// --- Global values ---

	@Test
	void testGlobalValuesPassedToSubcharts() {
		Chart subchart = simpleChart("redis", "17.0.0", List.of(tmpl("cm.yaml", "env: {{ .Values.global.env }}")),
				Map.of());

		Chart parent = Chart.builder()
			.metadata(ChartMetadata.builder().name("parent").version("1.0.0").build())
			.templates(List.of(tmpl("deploy.yaml", "kind: Deployment")))
			.values(Map.of())
			.dependencies(List.of(subchart))
			.build();

		Map<String, Object> vals = new HashMap<>();
		vals.put("global", new HashMap<>(Map.of("env", "production")));

		String result = engine.render(parent, vals, releaseInfo());
		assertTrue(result.contains("env: production"));
	}

	// --- Capabilities ---

	@Test
	void testCapabilitiesAvailable() {
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("test.yaml", "kube: {{ .Capabilities.KubeVersion.Version }}")), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("kube: v1.28.0"));
	}

	// --- Error handling ---

	@Test
	void testRenderThrowsOnInvalidTemplate() {
		// Invalid template syntax should throw TemplateRenderException
		Chart chart = simpleChart("mychart", "1.0.0", List.of(tmpl("bad.yaml", "{{ fail \"deliberate error\" }}")),
				Map.of());
		assertThrows(TemplateRenderException.class, () -> engine.render(chart, Map.of(), releaseInfo()));
	}

	// --- Render with no templates ---

	@Test
	void testRenderEmptyTemplates() {
		Chart chart = simpleChart("mychart", "1.0.0", List.of(), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.isEmpty());
	}

	// --- Depth limit ---

	@Test
	void testDepthLimitPreventsInfiniteNesting() {
		// Create a deeply nested subchart structure (depth > 3)
		Chart level4 = simpleChart("level4", "1.0.0", List.of(tmpl("l4.yaml", "level: 4")), Map.of());
		Chart level3 = Chart.builder()
			.metadata(ChartMetadata.builder().name("level3").version("1.0.0").build())
			.templates(List.of(tmpl("l3.yaml", "level: 3")))
			.values(Map.of())
			.dependencies(List.of(level4))
			.build();
		Chart level2 = Chart.builder()
			.metadata(ChartMetadata.builder().name("level2").version("1.0.0").build())
			.templates(List.of(tmpl("l2.yaml", "level: 2")))
			.values(Map.of())
			.dependencies(List.of(level3))
			.build();
		Chart level1 = Chart.builder()
			.metadata(ChartMetadata.builder().name("level1").version("1.0.0").build())
			.templates(List.of(tmpl("l1.yaml", "level: 1")))
			.values(Map.of())
			.dependencies(List.of(level2))
			.build();
		Chart root = Chart.builder()
			.metadata(ChartMetadata.builder().name("root").version("1.0.0").build())
			.templates(List.of(tmpl("root.yaml", "level: 0")))
			.values(Map.of())
			.dependencies(List.of(level1))
			.build();

		String result = engine.render(root, Map.of(), releaseInfo());
		// Root, level1, level2, level3 should render (depth 0-3)
		// level4 should be skipped (depth > 3)
		assertTrue(result.contains("level: 0"));
		assertTrue(result.contains("level: 1"));
		assertTrue(result.contains("level: 2"));
		assertTrue(result.contains("level: 3"));
		assertFalse(result.contains("level: 4"));
	}

	// --- Multiple renders don't leak state ---

	@Test
	void testMultipleRendersAreIsolated() {
		Chart chart1 = simpleChart("chart1", "1.0.0", List.of(tmpl("cm.yaml", "name: {{ .Values.name }}")),
				Map.of("name", "default1"));
		Chart chart2 = simpleChart("chart2", "1.0.0", List.of(tmpl("cm.yaml", "name: {{ .Values.name }}")),
				Map.of("name", "default2"));

		String result1 = engine.render(chart1, Map.of("name", "first"), releaseInfo());
		String result2 = engine.render(chart2, Map.of("name", "second"), releaseInfo());

		assertTrue(result1.contains("name: first"));
		assertTrue(result2.contains("name: second"));
		assertFalse(result1.contains("second"));
		assertFalse(result2.contains("first"));
	}

	// --- Null values handling ---

	@Test
	void testRenderWithNullChartValues() {
		Chart chart = Chart.builder()
			.metadata(ChartMetadata.builder().name("mychart").version("1.0.0").build())
			.templates(List.of(tmpl("cm.yaml", "kind: ConfigMap")))
			.values(null)
			.build();
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("kind: ConfigMap"));
	}

}
