package org.alexmond.jhelm.core.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.exception.TemplateRenderException;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
		return simpleChartWithFiles(name, version, templates, values, Map.of());
	}

	private Chart simpleChartWithFiles(String name, String version, List<Chart.Template> templates,
			Map<String, Object> values, Map<String, String> files) {
		return Chart.builder()
			.metadata(ChartMetadata.builder().name(name).version(version).build())
			.templates(templates)
			.values(values)
			.files(new LinkedHashMap<>(files))
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

	// --- .Subcharts context ---

	@Test
	void testSubchartsContextAvailable() {
		Chart subchart = simpleChart("prometheus-node-exporter", "4.0.0", List.of(tmpl("svc.yaml", "kind: Service")),
				Map.of());

		Chart parent = Chart.builder()
			.metadata(ChartMetadata.builder().name("kube-prometheus-stack").version("1.0.0").build())
			.templates(List
				.of(tmpl("test.yaml", "name: {{ (index .Subcharts \"prometheus-node-exporter\").Chart.Name }}")))
			.values(Map.of())
			.dependencies(List.of(subchart))
			.build();

		String result = engine.render(parent, Map.of(), releaseInfo());
		assertTrue(result.contains("name: prometheus-node-exporter"));
	}

	@Test
	void testSubchartsContextWithAlias() {
		Chart subchart = Chart.builder()
			.metadata(ChartMetadata.builder().name("redis").version("17.0.0").build())
			.templates(List.of(tmpl("svc.yaml", "kind: Service")))
			.values(Map.of())
			.alias("cache")
			.build();

		Chart parent = Chart.builder()
			.metadata(ChartMetadata.builder().name("parent").version("1.0.0").build())
			.templates(List.of(tmpl("test.yaml", "version: {{ .Subcharts.cache.Chart.Version }}")))
			.values(Map.of())
			.dependencies(List.of(subchart))
			.build();

		String result = engine.render(parent, Map.of(), releaseInfo());
		assertTrue(result.contains("version: 17.0.0"));
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

	@Test
	void testGlobalValuesDeepMergedWithSubchartGlobals() {
		// Subchart has its own global defaults; parent global should deep-merge, not
		// replace
		Chart subchart = simpleChart("postgresql-ha", "11.0.0", List.of(tmpl("cm.yaml",
				"storageClass: {{ .Values.global.storageClass }}\nregistry: {{ .Values.global.imageRegistry }}")),
				Map.of("global", new HashMap<>(Map.of("storageClass", "default-sc", "imageRegistry", "docker.io"))));

		Chart parent = Chart.builder()
			.metadata(ChartMetadata.builder().name("gitea").version("1.0.0").build())
			.templates(List.of(tmpl("deploy.yaml", "kind: Deployment")))
			.values(Map.of())
			.dependencies(List.of(subchart))
			.build();

		// Parent only overrides imageRegistry, not storageClass
		Map<String, Object> vals = new HashMap<>();
		vals.put("global", new HashMap<>(Map.of("imageRegistry", "gcr.io")));

		String result = engine.render(parent, vals, releaseInfo());
		// Parent override should win
		assertTrue(result.contains("registry: gcr.io"), "parent global should override: " + result);
		// Subchart default should be preserved (deep-merged, not replaced)
		assertTrue(result.contains("storageClass: default-sc"),
				"subchart global default should be preserved: " + result);
	}

	// --- Capabilities ---

	@Test
	void testCapabilitiesAvailable() {
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("test.yaml", "kube: {{ .Capabilities.KubeVersion.Version }}")), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("kube: v1.35.0"));
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

	// --- Capabilities: APIVersions.Has method invocation ---

	@Test
	void testApiVersionsHasReturnsTrue() {
		// .Capabilities.APIVersions.Has "policy/v1" should return true
		// because the engine includes policy/v1 in DEFAULT_API_VERSIONS
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("test.yaml",
						"{{ if .Capabilities.APIVersions.Has \"policy/v1\" }}policy-v1{{ else }}no-policy{{ end }}")),
				Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("policy-v1"));
	}

	@Test
	void testApiVersionsHasReturnsFalse() {
		// An unknown API version should not be found
		Chart chart = simpleChart("mychart", "1.0.0", List.of(tmpl("test.yaml",
				"{{ if .Capabilities.APIVersions.Has \"custom.example.com/v1beta1\" }}found{{ else }}not-found{{ end }}")),
				Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("not-found"));
	}

	@Test
	void testApiVersionsHasMultipleVersions() {
		// Verify several known API versions are available
		String template = """
				apps: {{ if .Capabilities.APIVersions.Has "apps/v1" }}yes{{ else }}no{{ end }}
				batch: {{ if .Capabilities.APIVersions.Has "batch/v1" }}yes{{ else }}no{{ end }}
				networking: {{ if .Capabilities.APIVersions.Has "networking.k8s.io/v1" }}yes{{ else }}no{{ end }}
				rbac: {{ if .Capabilities.APIVersions.Has "rbac.authorization.k8s.io/v1" }}yes{{ else }}no{{ end }}""";
		Chart chart = simpleChart("mychart", "1.0.0", List.of(tmpl("test.yaml", template)), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("apps: yes"));
		assertTrue(result.contains("batch: yes"));
		assertTrue(result.contains("networking: yes"));
		assertTrue(result.contains("rbac: yes"));
	}

	// --- Capabilities: KubeVersion fields ---

	@ParameterizedTest(name = "KubeVersion.{0} is accessible")
	@CsvSource({ "Version, v1.35.0", "GitVersion, v1.35.0", "Major, 1", "Minor, 35" })
	void testKubeVersionField(String field, String expected) {
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("test.yaml", "val: {{ .Capabilities.KubeVersion." + field + " }}")), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("val: " + expected));
	}

	// --- Chart.Annotations access ---

	@Test
	void testChartAnnotationsAccess() {
		Chart chart = Chart.builder()
			.metadata(ChartMetadata.builder()
				.name("mychart")
				.version("1.0.0")
				.annotations(Map.of("fips", "true", "category", "database"))
				.build())
			.templates(List.of(tmpl("test.yaml",
					"fips: {{ .Chart.annotations.fips }}\ncategory: {{ .Chart.annotations.category }}")))
			.values(Map.of())
			.build();
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("fips: true"));
		assertTrue(result.contains("category: database"));
	}

	@Test
	void testChartAnnotationsConditional() {
		// Simulate bitnami charts that check .Chart.Annotations for FIPS
		Chart chart = Chart.builder()
			.metadata(ChartMetadata.builder()
				.name("mychart")
				.version("1.0.0")
				.annotations(Map.of("fips", "true"))
				.build())
			.templates(List
				.of(tmpl("test.yaml", "{{ if .Chart.annotations.fips }}fips-enabled{{ else }}standard{{ end }}")))
			.values(Map.of())
			.build();
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("fips-enabled"));
	}

	// --- semverCompare from Sprig ---

	@Test
	void testSemverCompareInTemplate() {
		// semverCompare should properly compare versions — this was broken when
		// ChartFunctions had a stub that always returned true
		String template = """
				{{ if semverCompare ">=1.21-0" .Capabilities.KubeVersion.Version -}}
				modern-api
				{{- else -}}
				legacy-api
				{{- end }}""";
		Chart chart = simpleChart("mychart", "1.0.0", List.of(tmpl("test.yaml", template)), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("modern-api"));
	}

	@Test
	void testSemverCompareSelectsApiVersion() {
		// Common bitnami pattern: choose Ingress API version based on kube version
		String template = """
				{{- if semverCompare ">=1.19-0" .Capabilities.KubeVersion.Version -}}
				apiVersion: networking.k8s.io/v1
				{{- else -}}
				apiVersion: extensions/v1beta1
				{{- end }}""";
		Chart chart = simpleChart("mychart", "1.0.0", List.of(tmpl("ingress.yaml", template)), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("apiVersion: networking.k8s.io/v1"));
		assertFalse(result.contains("extensions/v1beta1"));
	}

	// --- Range over maps: sorted key iteration ---

	@Test
	void testRangeOverMapSortedKeys() {
		// Go text/template guarantees sorted-key iteration for string-keyed maps
		Map<String, Object> env = new HashMap<>();
		env.put("ZOO_PORT", "2181");
		env.put("APP_NAME", "myapp");
		env.put("LOG_LEVEL", "info");

		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("cm.yaml", "{{ range $k, $v := .Values.env }}{{ $k }}={{ $v }}\n{{ end }}")),
				Map.of("env", env));
		String result = engine.render(chart, Map.of(), releaseInfo());

		// Keys should appear in alphabetical order
		int appIdx = result.indexOf("APP_NAME");
		int logIdx = result.indexOf("LOG_LEVEL");
		int zooIdx = result.indexOf("ZOO_PORT");
		assertTrue(appIdx < logIdx && logIdx < zooIdx, "Expected sorted key order: APP_NAME < LOG_LEVEL < ZOO_PORT");
	}

	// --- printValue: Collection and Map rendering ---

	@Test
	void testCollectionPrintedAsGoFormat() {
		// Collections should print as [item1 item2] matching Go's fmt.Sprint
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("svc.yaml", "sourceRanges: {{ .Values.loadBalancerSourceRanges }}")),
				Map.of("loadBalancerSourceRanges", List.of("10.0.0.0/8", "172.16.0.0/12")));
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("sourceRanges: [10.0.0.0/8 172.16.0.0/12]"));
	}

	@Test
	void testMapPrintedAsGoFormat() {
		Map<String, String> labels = new LinkedHashMap<>();
		labels.put("app", "nginx");
		labels.put("tier", "frontend");

		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("test.yaml", "labels: {{ .Values.selectorLabels }}")), Map.of("selectorLabels", labels));
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("labels: map[app:nginx tier:frontend]"));
	}

	// --- Subchart default values merging ---

	@Test
	void testSubchartDefaultsAvailableInParent() {
		// Parent template should access subchart defaults via .Values.<subchart>.*
		Chart subchart = simpleChart("redis", "17.0.0", List.of(tmpl("svc.yaml", "kind: Service")),
				Map.of("port", 6379, "auth", Map.of("enabled", true)));

		Chart parent = Chart.builder()
			.metadata(ChartMetadata.builder().name("parent").version("1.0.0").build())
			.templates(List.of(tmpl("cm.yaml", "port: {{ .Values.redis.port }}")))
			.values(Map.of())
			.dependencies(List.of(subchart))
			.build();

		String result = engine.render(parent, Map.of(), releaseInfo());
		assertTrue(result.contains("port: 6379"));
	}

	@Test
	void testSubchartDefaultsMergedWithOverrides() {
		// User overrides should be deep-merged with subchart defaults
		Chart subchart = simpleChart("redis", "17.0.0", List.of(tmpl("svc.yaml", "kind: Service")),
				Map.of("port", 6379, "auth", new HashMap<>(Map.of("enabled", true, "password", "default"))));

		Chart parent = Chart.builder()
			.metadata(ChartMetadata.builder().name("parent").version("1.0.0").build())
			.templates(List.of(tmpl("cm.yaml",
					"port: {{ .Values.redis.port }}\nauth: {{ .Values.redis.auth.enabled }}\npwd: {{ .Values.redis.auth.password }}")))
			.values(Map.of())
			.dependencies(List.of(subchart))
			.build();

		Map<String, Object> overrides = new HashMap<>();
		overrides.put("redis", new HashMap<>(Map.of("auth", new HashMap<>(Map.of("password", "secret123")))));

		String result = engine.render(parent, overrides, releaseInfo());
		assertTrue(result.contains("port: 6379"));
		assertTrue(result.contains("auth: true"));
		assertTrue(result.contains("pwd: secret123"));
	}

	// --- YAML tilde null handling ---

	@Test
	void testDnsPolicyNullOmitted() {
		Map<String, Object> defaults = new HashMap<>();
		defaults.put("dnsPolicy", null);
		String template = """
				kind: Deployment
				spec:
				  template:
				    spec:
				    {{- if .Values.dnsPolicy }}
				      dnsPolicy: {{ .Values.dnsPolicy }}
				    {{- end }}
				      containers: []""";
		Chart chart = simpleChart("grafana", "1.0.0", List.of(tmpl("deploy.yaml", template)), defaults);
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertFalse(result.contains("dnsPolicy"), "null dnsPolicy should be omitted");
		assertTrue(result.contains("containers: []"));
	}

	// --- toYaml null preservation ---

	@Test
	void testToYamlPreservesNullValues() {
		// Go yaml.Marshal preserves nil map entries as "null" (no omitempty on maps)
		Map<String, Object> spec = new HashMap<>();
		spec.put("replicas", 3);
		spec.put("revisionHistoryLimit", null);
		spec.put("selector", "app=test");

		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("deploy.yaml", "spec:\n{{ .Values.spec | toYaml | indent 2 }}")), Map.of("spec", spec));
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("replicas: 3"), "non-null field should be present");
		assertTrue(result.contains("selector: app=test"), "non-null field should be present");
		assertTrue(result.contains("revisionHistoryLimit: null"), "null field should be rendered as null by toYaml");
	}

	// --- quote null-guard pattern (cert-manager) ---

	@Test
	void testQuoteNullGuardOmitsField() {
		// Cert-manager pattern: field guarded by quote+has should be omitted when
		// value is null
		Map<String, Object> global = new HashMap<>();
		global.put("revisionHistoryLimit", null);

		String template = """
				kind: Deployment
				spec:
				{{- if not (has (quote .Values.global.revisionHistoryLimit) (list "" (quote ""))) }}
				  revisionHistoryLimit: {{ .Values.global.revisionHistoryLimit }}
				{{- end }}
				  replicas: 1""";
		Chart chart = simpleChart("certmgr", "1.0.0", List.of(tmpl("deploy.yaml", template)), Map.of("global", global));
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertFalse(result.contains("revisionHistoryLimit"), "null-guarded field should be omitted");
		assertTrue(result.contains("replicas: 1"));
	}

	// --- include with $.Template.BasePath ---

	@Test
	void testIncludeWithTemplateBasePath() {
		// Pattern: include (print $.Template.BasePath "/configmap.yaml") .
		// This should resolve to "mychart/templates/configmap.yaml" and find the template
		Chart chart = simpleChart("mychart", "1.0.0", List.of(
				tmpl("configmap.yaml", "apiVersion: v1\nkind: ConfigMap\ndata:\n  key: value"),
				tmpl("deploy.yaml",
						"checksum: {{ include (print $.Template.BasePath \"/configmap.yaml\") . | sha256sum }}")),
				Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		// Should NOT be the SHA-256 of empty string
		assertFalse(result.contains("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
				"sha256sum should not hash empty string — include must resolve the template");
		assertTrue(result.contains("checksum: "));
	}

	@Test
	void testTemplateBasePathValue() {
		// $.Template.BasePath should be chartName/templates
		Chart chart = simpleChart("mychart", "1.0.0", List.of(tmpl("test.yaml", "basePath: {{ $.Template.BasePath }}")),
				Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("basePath: mychart/templates"));
	}

	@Test
	void testTemplateNameValue() {
		// $.Template.Name should be chartName/templates/fileName
		Chart chart = simpleChart("mychart", "1.0.0", List.of(tmpl("deploy.yaml", "name: {{ $.Template.Name }}")),
				Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("name: mychart/templates/deploy.yaml"));
	}

	@Test
	void testSubchartWithAliasDefaultsAvailable() {
		// Subchart with alias should have its defaults under the alias key
		Chart subchart = Chart.builder()
			.metadata(ChartMetadata.builder().name("redis").version("17.0.0").build())
			.templates(List.of(tmpl("svc.yaml", "kind: Service")))
			.values(Map.of("port", 6379))
			.alias("cache")
			.build();

		Chart parent = Chart.builder()
			.metadata(ChartMetadata.builder().name("parent").version("1.0.0").build())
			.templates(List.of(tmpl("cm.yaml", "cache-port: {{ .Values.cache.port }}")))
			.values(Map.of())
			.dependencies(List.of(subchart))
			.build();

		String result = engine.render(parent, Map.of(), releaseInfo());
		assertTrue(result.contains("cache-port: 6379"));
	}

	// --- Issue #137: nested parenthesized expressions ---

	@Test
	void testNestedParenthesizedExpressionsWithTernary() {
		// pgadmin4 pattern: tpl (ternary .toMap (.toMap | toYaml) (kindIs "string"
		// .toMap)) .context
		String helpers = """
				{{- define "mychart.tplToMap" -}}
				{{- tpl (ternary .toMap (.toMap | toYaml) (kindIs "string" .toMap)) .context -}}
				{{- end -}}
				""";
		String deployment = """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: test
				  labels:
				    {{ include "mychart.tplToMap" (dict "toMap" "app: myapp" "context" $) }}
				""";
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("_helpers.tpl", helpers), tmpl("configmap.yaml", deployment)), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("app: myapp"), "tplToMap should resolve string input: " + result);
	}

	@Test
	void testNestedParenthesizedExpressionsWithMapInput() {
		// When input is a map, kindIs "string" → false, so (.toMap | toYaml) branch is
		// chosen
		String helpers = """
				{{- define "mychart.tplToMap" -}}
				{{- tpl (ternary .toMap (.toMap | toYaml) (kindIs "string" .toMap)) .context -}}
				{{- end -}}
				""";
		String deployment = """
				apiVersion: v1
				kind: ConfigMap
				metadata:
				  name: test
				  labels:
				    {{ include "mychart.tplToMap" (dict "toMap" (dict "app" "myapp") "context" $) }}
				""";
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("_helpers.tpl", helpers), tmpl("configmap.yaml", deployment)), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("app: myapp"), "tplToMap should resolve map input via toYaml: " + result);
	}

	@Test
	void testPgadmin4HelpersFullParse() {
		// Test parsing the full pgadmin4 _helpers.tpl patterns
		String helpers = """
				{{- define "pgadmin.name" -}}
				{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
				{{- end -}}

				{{- define "pgadmin.fullname" -}}
				{{- if .Values.fullnameOverride -}}
				{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
				{{- else -}}
				{{- $name := default .Chart.Name .Values.nameOverride -}}
				{{- if contains $name .Release.Name -}}
				{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
				{{- else -}}
				{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
				{{- end -}}
				{{- end -}}
				{{- end -}}

				{{- define "pgadmin.tplToMap" -}}
				{{- tpl (ternary .toMap (.toMap | toYaml) (kindIs "string" .toMap)) .context -}}
				{{- end -}}

				{{- define "pgadmin.ingress.apiVersion" -}}
				{{- if and ($.Capabilities.APIVersions.Has "networking.k8s.io/v1") (semverCompare ">= 1.19-0" .Capabilities.KubeVersion.Version) }}
				{{- print "networking.k8s.io/v1" }}
				{{- else if $.Capabilities.APIVersions.Has "networking.k8s.io/v1beta1" }}
				{{- print "networking.k8s.io/v1beta1" }}
				{{- else }}
				{{- print "extensions/v1beta1" }}
				{{- end }}
				{{- end }}

				{{- define "deployment.apiVersion" -}}
				{{- print "apps/v1" -}}
				{{- end -}}

				{{- define "pgadmin.validateValues" -}}
				{{- $problems := list -}}
				{{- $_ := set $.Values "serverDefinitions" (default (dict) $.Values.serverDefinitions) -}}
				{{- $type := default "" $.Values.serverDefinitions.resourceType -}}
				{{- if and $.Values.serverDefinitions.enabled (not (or (eq $type "ConfigMap") (eq $type "Secret"))) -}}
				{{- $problems = append $problems "serverDefinitions.resourceType must be 'ConfigMap' or 'Secret'" -}}
				{{- end -}}
				{{- if gt (len $problems) 0 -}}
				{{- fail (printf "VALIDATION: %s" (join ", " $problems)) -}}
				{{- end -}}
				{{- end -}}
				""";
		String deployment = """
				apiVersion: {{ template "deployment.apiVersion" . }}
				kind: Deployment
				metadata:
				  name: {{ include "pgadmin.fullname" . }}
				""";
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("_helpers.tpl", helpers), tmpl("deployment.yaml", deployment)), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("apiVersion: apps/v1"), "deployment.apiVersion should render: " + result);
	}

	// --- Issue #135: subchart version labels ---

	@Test
	void testCommonImagesVersionWithSemverTag() {
		// bitnami/minio common.images.version pattern with backtick regex
		String helpers = """
				{{- define "common.images.version" -}}
				{{- $imageTag := .imageRoot.tag | toString -}}
				{{- if regexMatch `^([0-9]+)(\\.[0-9]+)?(\\.[0-9]+)?(-([0-9A-Za-z\\-]+(\\.[0-9A-Za-z\\-]+)*))?(\\+([0-9A-Za-z\\-]+(\\.[0-9A-Za-z\\-]+)*))?$` $imageTag -}}
				    {{- $version := semver $imageTag -}}
				    {{- printf "%d.%d.%d" $version.Major $version.Minor $version.Patch -}}
				{{- else -}}
				    {{- print .chart.AppVersion -}}
				{{- end -}}
				{{- end -}}
				""";
		String deployment = """
				version: {{ include "common.images.version" (dict "imageRoot" (dict "tag" "2.0.2-debian-12-r3") "chart" .Chart) }}
				""";
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("_helpers.tpl", helpers), tmpl("deployment.yaml", deployment)), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("version: 2.0.2"),
				"Should extract semver 2.0.2 from tag 2.0.2-debian-12-r3: " + result);
	}

	@Test
	void testCommonImagesVersionDebug() {
		// Test each step individually to find where it fails
		String helpers = """
				{{- define "test.debug" -}}
				tag={{ .imageRoot.tag }}|toString={{ .imageRoot.tag | toString }}|match={{ regexMatch `^([0-9]+)(\\.[0-9]+)?(\\.[0-9]+)?(-([0-9A-Za-z\\-]+(\\.[0-9A-Za-z\\-]+)*))?(\\+([0-9A-Za-z\\-]+(\\.[0-9A-Za-z\\-]+)*))?$` (.imageRoot.tag | toString) }}
				{{- end -}}
				""";
		Map<String, Object> consoleImage = new HashMap<>();
		consoleImage.put("tag", "2.0.2-debian-12-r3");
		Map<String, Object> values = new HashMap<>();
		values.put("console", Map.of("image", consoleImage));

		String deployment = """
				{{ include "test.debug" (dict "imageRoot" .Values.console.image "chart" .Chart) }}
				""";
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("_helpers.tpl", helpers), tmpl("deployment.yaml", deployment)), values);
		String result = engine.render(chart, Map.of(), releaseInfo());
		// Check that regexMatch returns true
		assertTrue(result.contains("match=true"), "regexMatch should match 2.0.2-debian-12-r3: " + result);
	}

	// --- Issue #134: quote null and regexMatch substring ---

	@Test
	void testQuoteNullOmitsReplicas() {
		// gitlab-runner pattern: {{- if and (not .Values.hpa) (not (quote
		// .Values.replicas | empty)) }}
		String helpers = """
				{{- define "test.replicas" -}}
				{{- if and (not .Values.hpa) (not (quote .Values.replicas | empty)) -}}
				replicas: {{ .Values.replicas }}
				{{- end -}}
				{{- end -}}
				""";
		String deployment = """
				spec:
				  {{ include "test.replicas" . }}
				  revisionHistoryLimit: 10
				""";
		// replicas and hpa not set (null)
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("_helpers.tpl", helpers), tmpl("deployment.yaml", deployment)), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertFalse(result.contains("replicas:"), "replicas should be omitted when null: " + result);
		assertTrue(result.contains("revisionHistoryLimit: 10"), "other fields should render: " + result);
	}

	@Test
	void testQuoteNullEnvValue() {
		// gitlab-runner pattern: value: {{ include "gitlab-runner.gitlabUrl" . }}
		// where gitlabUrl is: {{- .Values.gitlabUrl | quote -}}
		String helpers = """
				{{- define "test.url" -}}
				{{- .Values.gitlabUrl | quote -}}
				{{- end -}}
				""";
		String deployment = """
				env:
				- name: CI_SERVER_URL
				  value: {{ include "test.url" . }}
				""";
		// gitlabUrl not set (null)
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("_helpers.tpl", helpers), tmpl("deployment.yaml", deployment)), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		// With null gitlabUrl, quote returns empty string, so value: has no content after
		// it
		assertFalse(result.contains("value: \"\""), "null gitlabUrl should not produce quoted empty: " + result);
	}

	@Test
	void testRegexMatchSubstringInConfig() {
		// gitlab-runner pattern: regexMatch "\\s*namespace\\s*=" .Values.runners.config
		String helpers = """
				{{- define "test.env" -}}
				{{- if not (regexMatch "\\\\s*namespace\\\\s*=" .Values.runners.config) -}}
				- name: KUBERNETES_NAMESPACE
				  value: {{ .Release.Namespace | quote }}
				{{- end -}}
				{{- end -}}
				""";
		String deployment = """
				env:
				{{ include "test.env" . }}
				""";
		Map<String, Object> values = new HashMap<>();
		Map<String, Object> runners = new HashMap<>();
		runners.put("config", "[[runners]]\n  [runners.kubernetes]\n    namespace = \"default\"");
		values.put("runners", runners);
		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("_helpers.tpl", helpers), tmpl("deployment.yaml", deployment)), values);
		String result = engine.render(chart, Map.of(), releaseInfo());
		// namespace = is in config, so regexMatch should be true, and
		// KUBERNETES_NAMESPACE should be omitted
		assertFalse(result.contains("KUBERNETES_NAMESPACE"),
				"KUBERNETES_NAMESPACE should be omitted when namespace is in config: " + result);
	}

	// --- Issue #130: explicit null fields via fromYaml | toYaml ---

	@Test
	void testNullFieldPreservedInFromYamlToYamlPipeline() {
		// Traefik pattern: include template | fromYaml | toYaml preserves null fields
		// When lifecycle:{} is passed through with(empty) → key: with no value → fromYaml
		// parses as null → toYaml should serialize as "lifecycle: null"
		String helpers = """
				{{- define "pod" -}}
				lifecycle:
				  {{- with .Values.deployment.lifecycle }}
				  {{- toYaml . | nindent 2 }}
				  {{- end }}
				ports:
				- containerPort: 80
				{{- end -}}""";
		String deploy = """
				spec:
				  {{ include "pod" . | fromYaml | toYaml | nindent 2 }}""";
		Map<String, Object> values = new HashMap<>();
		Map<String, Object> deployment = new HashMap<>();
		deployment.put("lifecycle", new HashMap<>());
		values.put("deployment", deployment);

		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("_helpers.tpl", helpers), tmpl("deploy.yaml", deploy)), values);
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("lifecycle: null"),
				"lifecycle: null should be preserved through fromYaml | toYaml: " + result);
	}

	@Test
	void testNullFieldPreservedWithNullValue() {
		// When lifecycle value is null (not empty map), same behavior expected
		String helpers = """
				{{- define "pod" -}}
				lifecycle:
				  {{- with .Values.deployment.lifecycle }}
				  {{- toYaml . | nindent 2 }}
				  {{- end }}
				name: test
				{{- end -}}""";
		String deploy = """
				spec:
				  {{ include "pod" . | fromYaml | toYaml | nindent 2 }}""";
		Map<String, Object> values = new HashMap<>();
		Map<String, Object> deployment = new HashMap<>();
		deployment.put("lifecycle", null);
		values.put("deployment", deployment);

		Chart chart = simpleChart("mychart", "1.0.0",
				List.of(tmpl("_helpers.tpl", helpers), tmpl("deploy.yaml", deploy)), values);
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("lifecycle: null"),
				"lifecycle: null should be preserved when value is null: " + result);
	}

	// --- Issue #132: Range over map with variable field access ---

	@Test
	void testRangeSetMutatesDictInOuterScope() {
		// Traefik service.yaml pattern: set mutates dict from inner range, used in outer
		// scope
		String template = """
				{{- $ports := dict -}}
				{{- range $name, $config := .Values.items -}}
				  {{- $_ := set $ports $name $config -}}
				{{- end -}}
				{{- range $k, $v := $ports }}
				{{ $k }}: {{ $v }}
				{{- end }}""";
		Map<String, Object> values = new HashMap<>();
		Map<String, Object> items = new LinkedHashMap<>();
		items.put("a", "alpha");
		items.put("b", "beta");
		values.put("items", items);

		Chart chart = simpleChart("mychart", "1.0.0", List.of(tmpl("test.yaml", template)), values);
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("a: alpha"), "Ports populated by set should be visible: " + result);
		assertTrue(result.contains("b: beta"), "Ports populated by set should be visible: " + result);
	}

	@Test
	void testRangeOverMapVariableFieldAccess() {
		// Traefik service-ports pattern: range over map, access fields on loop var
		String helpers = """
				{{- define "ports" -}}
				{{- range $name, $config := .ports }}
				{{- if (index (default dict $config.expose) $.serviceName) }}
				- port: {{ default $config.port $config.exposedPort }}
				  name: {{ $name }}
				{{- end }}
				{{- end }}
				{{- end -}}""";
		String svc = """
				ports:
				{{ include "ports" .Values.svcContext }}""";

		Map<String, Object> web = new HashMap<>();
		web.put("port", 8000);
		web.put("expose", Map.of("default", true));
		web.put("exposedPort", 80);
		Map<String, Object> websecure = new HashMap<>();
		websecure.put("port", 8443);
		websecure.put("expose", Map.of("default", true));
		websecure.put("exposedPort", 443);
		Map<String, Object> ports = new LinkedHashMap<>();
		ports.put("web", web);
		ports.put("websecure", websecure);

		Map<String, Object> svcContext = new HashMap<>();
		svcContext.put("ports", ports);
		svcContext.put("serviceName", "default");
		Map<String, Object> values = Map.of("svcContext", svcContext);

		Chart chart = simpleChart("mychart", "1.0.0", List.of(tmpl("_helpers.tpl", helpers), tmpl("svc.yaml", svc)),
				values);
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("port: 80"), "Should render web port 80: " + result);
		assertTrue(result.contains("port: 443"), "Should render websecure port 443: " + result);
	}

	@Test
	void testTraefikServiceNestedRangePattern() {
		// Full traefik service.yaml pattern: outer range over services, inner range over
		// ports with $config.protocol field access and set to build $tcpPorts
		String helpers = """
				{{- define "svc-ports" -}}
				{{- range $portName, $config := .ports }}
				{{- if (index (default dict $config.expose) $.serviceName) }}
				- port: {{ default $config.port $config.exposedPort }}
				  name: {{ $portName }}
				{{- end }}
				{{- end }}
				{{- end -}}""";
		String svc = """
				{{- $tcpPorts := dict -}}
				{{- range $portName, $config := .Values.ports -}}
				  {{- if $config -}}
				    {{- if eq (toString (default "TCP" $config.protocol)) "TCP" -}}
				      {{- $_ := set $tcpPorts $portName $config -}}
				    {{- end -}}
				  {{- end -}}
				{{- end -}}
				ports:
				{{- template "svc-ports" (dict "ports" $tcpPorts "serviceName" "default") }}""";
		Map<String, Object> web = new HashMap<>();
		web.put("port", 8000);
		web.put("expose", Map.of("default", true));
		web.put("exposedPort", 80);
		web.put("protocol", "TCP");
		Map<String, Object> websecure = new HashMap<>();
		websecure.put("port", 8443);
		websecure.put("expose", Map.of("default", true));
		websecure.put("exposedPort", 443);
		websecure.put("protocol", "TCP");

		Map<String, Object> ports = new LinkedHashMap<>();
		ports.put("web", web);
		ports.put("websecure", websecure);
		Map<String, Object> values = Map.of("ports", ports);

		Chart chart = simpleChart("mychart", "1.0.0", List.of(tmpl("_helpers.tpl", helpers), tmpl("svc.yaml", svc)),
				values);
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("port: 80"), "Should render web port 80: " + result);
		assertTrue(result.contains("port: 443"), "Should render websecure port 443: " + result);
	}

	// --- split() return type and Chart.AppVersion access ---

	@Test
	void testChartAppVersionAccess() {
		String tmpl = "version: {{ .Chart.AppVersion }}";
		Map<String, Object> values = new HashMap<>();
		Chart chart = Chart.builder()
			.metadata(ChartMetadata.builder().name("mychart").version("1.0.0").appVersion("v3.6.7").build())
			.templates(List.of(tmpl("test.yaml", tmpl)))
			.values(values)
			.build();
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("version: v3.6.7"), "Chart.AppVersion should be accessible: " + result);
	}

	@Test
	void testSplitReturnMapWithUnderscoreKeys() {
		// Sprig split returns map with _0, _1, etc. keys — used in traefik proxyVersion
		String helpers = """
				{{- define "getVersion" -}}
				{{- $t := .Values.image.tag -}}
				{{- (split "@" (default .Chart.AppVersion $t))._0 -}}
				{{- end -}}""";
		String tmpl = """
				{{- $v := include "getVersion" . -}}
				version: {{ $v }}
				gte34: {{ semverCompare ">=v3.4.0-0" $v }}""";
		Map<String, Object> values = new HashMap<>();
		values.put("image", Map.of("tag", ""));
		Chart chart = Chart.builder()
			.metadata(ChartMetadata.builder().name("mychart").version("1.0.0").appVersion("v3.6.7").build())
			.templates(List.of(tmpl("_helpers.tpl", helpers), tmpl("test.yaml", tmpl)))
			.values(values)
			.build();
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("version: v3.6.7"), "split._0 should extract version: " + result);
		assertTrue(result.contains("gte34: true"), "semverCompare should work with split result: " + result);
	}

	// --- .Files object ---

	@Test
	void testFilesGetReturnsContent() {
		Map<String, String> files = Map.of("config.ini", "key=value");
		Chart chart = simpleChartWithFiles("mychart", "1.0.0",
				List.of(tmpl("test.yaml", "data: {{ .Files.Get \"config.ini\" }}")), Map.of(), files);
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("data: key=value"), "Files.Get should return file content: " + result);
	}

	@Test
	void testFilesGetMissingReturnsEmpty() {
		Chart chart = simpleChartWithFiles("mychart", "1.0.0",
				List.of(tmpl("test.yaml", "data: [{{ .Files.Get \"missing.txt\" }}]")), Map.of(), Map.of());
		String result = engine.render(chart, Map.of(), releaseInfo());
		assertTrue(result.contains("data: []"), "Files.Get for missing file should return empty: " + result);
	}

}
