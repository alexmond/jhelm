package org.alexmond.jhelm.core;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.config.JhelmTestProperties;
import org.alexmond.jhelm.core.config.KpsTestConfig;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.RepoManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@SpringBootTest
@ContextConfiguration(classes = KpsTestConfig.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KpsComparisonTest {

	private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();

	@Autowired
	private JhelmTestProperties testProperties;

	private final RepoManager repoManager = createRepoManager();

	private final ChartLoader chartLoader = new ChartLoader();

	private final Engine engine = new Engine();

	private final InstallAction installAction = new InstallAction(engine, null);

	private final JsonMapper objectMapper = JsonMapper.builder().build();

	private final Set<String> addedRepos = new HashSet<>();

	private RepoManager createRepoManager() {
		RepoManager rm = new RepoManager();
		rm.setInsecureSkipTlsVerify(true);
		return rm;
	}

	@Test
	void testSearchAndCompare() throws Exception {
		String query = "bitnami/nginx";
		String repo = "bitnami";
		String chart = "nginx";
		String repoUrl = "https://charts.bitnami.com/bitnami";

		addHelmRepo(repo, repoUrl);
		var versions = repoManager.getChartVersions(repo, chart);
		assertFalse(versions.isEmpty());

		// Helm search
		String helmOutput = runHelmSearchRepo(query);
		if (helmOutput != null) {
			log.info("Helm search output for {}:\n{}", query, helmOutput);
			// Verify our latest version matches Helm's latest version (first line of
			// output)
			String latestJHelm = versions.getFirst().getChartVersion();
			assertTrue(helmOutput.contains(latestJHelm),
					"Helm output should contain jhelm latest version " + latestJHelm);
		}
	}

	private String runHelmSearchRepo(String query) {
		try {
			ProcessBuilder pb = new ProcessBuilder("helm", "search", "repo", query);
			Process process = pb.start();

			String output;
			try (InputStream is = process.getInputStream();
					Scanner s = new Scanner(is, StandardCharsets.UTF_8).useDelimiter("\\A")) {
				output = s.hasNext() ? s.next() : "";
			}

			int exitCode = process.waitFor();
			if (exitCode != 0) {
				return null;
			}
			return output;
		}
		catch (Exception ex) {
			log.error("Failed to run helm search", ex);
			return null;
		}
	}

	@Test
	void testSimpleRendering() throws Exception {
		Chart chart = Chart.builder()
			.metadata(ChartMetadata.builder().name("simple").build())
			.values(Map.of("enabled", true, "name", "world"))
			.templates(new ArrayList<>(List.of(Chart.Template.builder()
				.name("hello.yaml")
				.data("hello {{ .Values.name }} {{ if .Values.enabled }}enabled{{ end }}")
				.build())))
			.build();

		Release release = installAction.install(chart, "simple", "default", Map.of(), 1, false);
		log.info("Simple manifest: [{}]", release.getManifest().trim());
		assertTrue(release.getManifest().contains("hello world enabled"));
	}

	@Test
	void testPullAndCompare() throws Exception {
		// This test demonstrates using the jhelm API to "pull" (simulated by using local
		// sample-charts directory)
		// Since we don't have a real Helm repo set up in CI, we use the local directory
		// as our "repo"
		File repoDir = new File("sample-charts");
		if (!repoDir.exists()) {
			repoDir = new File("../sample-charts");
		}

		File nginxDir = new File(repoDir, "nginx");
		if (nginxDir.exists()) {
			compareChart("nginx", "pulled-nginx", "bitnami", "https://charts.bitnami.com/bitnami");
		}
	}

	@ParameterizedTest
	@CsvFileSource(resources = "/single.csv")
	void compareSingleChart(String chartName, String repoId, String repoUrl) throws Exception {
		compareChart(chartName, "release-" + chartName, repoId, repoUrl);
	}

	@ParameterizedTest
	@CsvFileSource(resources = "/charts.csv")
	void compareAllTopCharts(String chartName, String repoId, String repoUrl) throws Exception {
		compareChart(chartName, "release-" + chartName, repoId, repoUrl);
	}

	private void compareChart(String chartName, String releaseName, String repoId, String repoUrl) throws Exception {
		// No charts skipped anymore

		File chartDir = findChartDir(chartName);

		if (chartDir == null) {
			log.info("Chart {} not found locally, fetching from repository {}...", chartName, repoUrl);
			try {
				addHelmRepo(repoId, repoUrl);
				fetchFromHelmRepo(chartName);
				chartDir = findChartDir(chartName);
			}
			catch (Exception ex) {
				log.error("Failed to fetch chart {} from repository: {}", chartName, ex.getMessage());
				fail("Failed to fetch chart " + chartName + " from repository: " + ex.getMessage());
			}
		}

		if (chartDir == null) {
			fail("Skipping chart " + chartName + " - directory not found and could not be fetched");
		}

		// Sanitize release name to be valid for Helm (alphanumeric and hyphens only, no
		// slashes)
		String sanitizedReleaseName = releaseName.replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
		if (sanitizedReleaseName.startsWith("-")) {
			sanitizedReleaseName = "r" + sanitizedReleaseName;
		}
		if (sanitizedReleaseName.endsWith("-")) {
			sanitizedReleaseName = sanitizedReleaseName.substring(0, sanitizedReleaseName.length() - 1);
		}

		log.info("Using sanitized release name: {} (from {})", sanitizedReleaseName, releaseName);

		String sanitizedName = chartName.replace("/", "_");

		// STEP 1: Run Helm template FIRST and save output
		log.info("{} - Running Helm template first...", chartName);
		String helmManifest = runHelmInstallDryRun(chartDir, sanitizedReleaseName, "default");

		if (helmManifest == null) {
			// Helm failed - log error and do NOT continue to Java rendering
			log.error("{} - Helm template failed, skipping Java rendering", chartName);
			fail(chartName + " - Helm template command failed - see logs for details");
		}

		// Save Helm output to target/helm-output/
		File helmOutputDir = new File("target/helm-output");
		helmOutputDir.mkdirs();
		File helmOutputFile = new File(helmOutputDir, sanitizedName + ".yaml");
		Files.writeString(helmOutputFile.toPath(), helmManifest);
		log.info("{} - Saved Helm output to {}", chartName, helmOutputFile.getPath());

		// STEP 2: Load chart and run Java rendering (only if Helm succeeded)
		Chart chart = chartLoader.load(chartDir);
		assertNotNull(chart);

		try {
			log.info("{} - Running JHelm rendering...", chartName);
			// JHelm dry-run
			Release release = installAction.install(chart, sanitizedReleaseName, "default", Map.of(), 1, true);
			assertNotNull(release);

			String jhelmManifest = release.getManifest();
			log.info("{} - JHelm manifest length: {}", chartName, jhelmManifest.length());

			File actualFile = new File("target/test-output/actual_" + sanitizedName + ".yaml");
			actualFile.getParentFile().mkdirs();
			Files.writeString(actualFile.toPath(), jhelmManifest);

			File expectedFile = new File("target/test-output/expected_" + sanitizedName + ".yaml");
			Files.writeString(expectedFile.toPath(), helmManifest);

			// Compare manifests
			compareManifests(chartName, jhelmManifest, helmManifest);

		}
		catch (Exception ex) {
			log.error("{} - JHelm rendering failed", chartName, ex);
			fail(chartName + " - JHelm rendering failed: " + ex.getMessage());
		}
	}

	private String runHelmInstallDryRun(File chartDir, String releaseName, String namespace) {
		try {
			ProcessBuilder pb = new ProcessBuilder("helm", "template", releaseName, chartDir.getAbsolutePath(),
					"--namespace", namespace);
			Process process = pb.start();

			String output;
			try (InputStream is = process.getInputStream();
					Scanner s = new Scanner(is, StandardCharsets.UTF_8).useDelimiter("\\A")) {
				output = s.hasNext() ? s.next() : "";
			}

			int exitCode = process.waitFor();
			if (exitCode != 0) {
				try (InputStream es = process.getErrorStream();
						Scanner s = new Scanner(es, StandardCharsets.UTF_8).useDelimiter("\\A")) {
					String error = s.hasNext() ? s.next() : "";
					log.error("Helm failed with exit code {}: {}", exitCode, error);
				}
				return null;
			}

			// helm template outputs the manifest directly (no MANIFEST: prefix)
			// Just return the output, but skip any leading/trailing whitespace
			if (output == null || output.trim().isEmpty()) {
				return null;
			}

			return output.trim();
		}
		catch (Exception ex) {
			log.error("Failed to run helm", ex);
			return null;
		}
	}

	private void compareManifests(String chartName, String jhelm, String helm) throws Exception {
		// Parse both manifests into YAML documents
		List<JsonNode> jhelmDocs = parseYamlDocuments(jhelm);
		List<JsonNode> helmDocs = parseYamlDocuments(helm);

		log.info("{} - JHelm documents: {}, Helm documents: {}", chartName, jhelmDocs.size(), helmDocs.size());

		// Create a map of documents by kind and name for order-independent comparison
		var jhelmMap = buildResourceMap(jhelmDocs);
		var helmMap = buildResourceMap(helmDocs);

		// Load ignore rules for this chart (needed for both missing-resource and
		// diff filtering)
		List<IgnoreRule> ignoreRules = loadIgnoreRules(chartName);

		// Check for missing resources, filtering out those covered by ignore rules
		var missingInJhelm = new LinkedHashSet<>(helmMap.keySet());
		missingInJhelm.removeAll(jhelmMap.keySet());
		missingInJhelm.removeIf((key) -> isIgnored(key, "*", ignoreRules));

		var extraInJhelm = new LinkedHashSet<>(jhelmMap.keySet());
		extraInJhelm.removeAll(helmMap.keySet());

		List<String> failures = new ArrayList<>();

		if (!missingInJhelm.isEmpty()) {
			failures.add("Resources missing in JHelm: " + missingInJhelm);
		}

		if (!extraInJhelm.isEmpty()) {
			log.warn("{} - Extra resources in JHelm (not in Helm): {}", chartName, extraInJhelm);
		}

		// Compare each resource — fast-path with equals(), detailed diff only when needed
		for (String key : helmMap.keySet()) {
			if (!jhelmMap.containsKey(key)) {
				continue;
			}

			JsonNode helmDoc = helmMap.get(key);
			JsonNode jhelmDoc = jhelmMap.get(key);

			// Fast path: skip expensive diff computation if trees are identical
			if (helmDoc.equals(jhelmDoc)) {
				continue;
			}

			List<Diff> diffs = computeDiffs(helmDoc, jhelmDoc, "");
			List<Diff> unignored = diffs.stream()
				.filter((d) -> !isIgnored(key, d.path(), ignoreRules))
				.collect(Collectors.toList());

			if (!unignored.isEmpty()) {
				int ignored = diffs.size() - unignored.size();
				StringBuilder sb = new StringBuilder();
				sb.append("Resource ").append(key).append(" has ").append(unignored.size()).append(" diff(s)");
				if (ignored > 0) {
					sb.append(" (").append(ignored).append(" ignored)");
				}
				sb.append(":\n");
				for (Diff d : unignored) {
					sb.append("  ").append(d).append('\n');
				}
				failures.add(sb.toString());
			}
		}

		if (failures.isEmpty()) {
			log.info("{} - All resources match Helm output!", chartName);
		}
		else {
			StringBuilder msg = new StringBuilder();
			msg.append(chartName).append(" - ").append(failures.size()).append(" comparison failure(s):\n");
			for (String f : failures) {
				msg.append("  - ").append(f).append('\n');
			}
			fail(msg.toString());
		}
	}

	private List<JsonNode> parseYamlDocuments(String yaml) throws Exception {
		List<JsonNode> docs = new ArrayList<>();
		YAMLMapper yamlMapper = YAMLMapper.builder().build();

		// Split by YAML document separator "---" with various whitespace patterns
		// Pattern matches "---" at the start of a line, with optional whitespace
		// before/after
		String[] parts = yaml.split("\\r?\\n---\\r?\\n");

		for (int i = 0; i < parts.length; i++) {
			String doc = parts[i].trim();

			// Skip empty documents
			if (doc.isEmpty()) {
				continue;
			}

			// Skip comment-only documents
			if (doc.startsWith("#") && !doc.contains("\n")) {
				continue;
			}

			try {
				JsonNode node = yamlMapper.readTree(doc);
				if (node != null && !node.isNull() && !node.isEmpty()) {
					docs.add(node);
				}
				else {
					log.warn("Skipping null/empty YAML node at position {}", i);
				}
			}
			catch (Exception ex) {
				log.warn("Skipping unparseable YAML fragment at position {}: {}. Doc preview: {}", i, ex.getMessage(),
						doc.substring(0, Math.min(100, doc.length())));
			}
		}

		return docs;
	}

	private Map<String, JsonNode> buildResourceMap(List<JsonNode> docs) {
		Map<String, JsonNode> map = new HashMap<>();

		for (JsonNode doc : docs) {
			String kind = doc.has("kind") ? doc.get("kind").asString() : "Unknown";
			String name = "unnamed";

			if (doc.has("metadata") && doc.get("metadata").has("name")) {
				name = doc.get("metadata").get("name").asString();
			}

			String key = kind + "/" + name;
			map.put(key, doc);
		}

		return map;
	}

	private List<Diff> computeDiffs(JsonNode expected, JsonNode actual, String path) {
		List<Diff> diffs = new ArrayList<>();

		if (expected.isObject() && actual.isObject()) {
			Set<String> allKeys = new LinkedHashSet<>(expected.propertyNames());
			allKeys.addAll(actual.propertyNames());
			for (String key : allKeys) {
				String childPath = path.isEmpty() ? key : path + "." + key;
				if (!expected.has(key)) {
					diffs.add(new Diff(childPath, "<missing>", actual.get(key).toString()));
				}
				else if (!actual.has(key)) {
					diffs.add(new Diff(childPath, expected.get(key).toString(), "<missing>"));
				}
				else {
					diffs.addAll(computeDiffs(expected.get(key), actual.get(key), childPath));
				}
			}
		}
		else if (expected.isArray() && actual.isArray()) {
			int max = Math.max(expected.size(), actual.size());
			for (int i = 0; i < max; i++) {
				String childPath = path + "[" + i + "]";
				if (i >= expected.size()) {
					diffs.add(new Diff(childPath, "<missing>", actual.get(i).toString()));
				}
				else if (i >= actual.size()) {
					diffs.add(new Diff(childPath, expected.get(i).toString(), "<missing>"));
				}
				else {
					diffs.addAll(computeDiffs(expected.get(i), actual.get(i), childPath));
				}
			}
		}
		else if (!expected.equals(actual)) {
			if (expected.isTextual() && actual.isTextual()) {
				try {
					JsonNode parsedExpected = YAML_MAPPER.readTree(expected.textValue());
					JsonNode parsedActual = YAML_MAPPER.readTree(actual.textValue());
					if (parsedExpected != null && parsedActual != null && !parsedExpected.isValueNode()
							&& !parsedActual.isValueNode()) {
						diffs.addAll(computeDiffs(parsedExpected, parsedActual, path));
						return diffs;
					}
				}
				catch (Exception ignored) {
				}
			}
			diffs.add(new Diff(path, expected.toString(), actual.toString()));
		}

		return diffs;
	}

	private List<IgnoreRule> loadIgnoreRules(String chartName) {
		Map<String, List<JhelmTestProperties.IgnoreRule>> ignores = testProperties.getComparisonIgnores();
		if (ignores == null || ignores.isEmpty()) {
			return Collections.emptyList();
		}
		List<IgnoreRule> rules = new ArrayList<>();
		// Load global rules (key "_global") and chart-specific rules
		for (String key : List.of("_global", chartName)) {
			List<JhelmTestProperties.IgnoreRule> entries = ignores.get(key);
			if (entries != null) {
				for (JhelmTestProperties.IgnoreRule entry : entries) {
					rules.add(new IgnoreRule(entry.getResource(), entry.getPath(), entry.getReason()));
				}
			}
		}
		return rules;
	}

	private boolean isIgnored(String resourceKey, String diffPath, List<IgnoreRule> rules) {
		for (IgnoreRule rule : rules) {
			if (pathMatches(resourceKey, rule.resource()) && pathMatches(diffPath, rule.path())) {
				log.debug("Ignoring diff at {} in {} (reason: {})", diffPath, resourceKey, rule.reason());
				return true;
			}
		}
		return false;
	}

	private boolean pathMatches(String value, String pattern) {
		if ("*".equals(pattern)) {
			return true;
		}
		// Prefix match: "path.*" matches any path starting with "path" followed by
		// ".", "[", or "/"
		if (pattern.endsWith(".*")) {
			String stem = pattern.substring(0, pattern.length() - 2);
			return value.equals(stem) || value.startsWith(stem + ".") || value.startsWith(stem + "[")
					|| value.startsWith(stem + "/");
		}
		// Simple trailing wildcard: "Secret/*" matches "Secret/name"
		if (pattern.endsWith("*")) {
			return value.startsWith(pattern.substring(0, pattern.length() - 1));
		}
		return value.equals(pattern);
	}

	private File findChartDir(String chartFullName) {
		String chartName = chartFullName.contains("/") ? chartFullName.substring(chartFullName.lastIndexOf("/") + 1)
				: chartFullName;
		String[] paths = { "target/temp-charts/" + chartName, "target/temp-charts/" + chartFullName,
				"sample-charts/" + chartName, "../sample-charts/" + chartName, "target/test-charts/" + chartName,
				"target/test-charts/bitnami/" + chartName, // common subfolder in bitnami
															// tgz
				chartName, // legacy check
				"../" + chartName };
		for (String p : paths) {
			File f = new File(p);
			if (f.exists() && f.isDirectory() && new File(f, "Chart.yaml").exists()) {
				return f;
			}
		}
		return null;
	}

	private void addHelmRepo(String repoId, String repoUrl) throws Exception {
		if (addedRepos.contains(repoId)) {
			log.debug("Repo {} already added in this session, skipping", repoId);
			return;
		}
		log.info("Adding repo {} at {} via RepoManager", repoId, repoUrl);
		repoManager.addRepo(repoId, repoUrl);
		try {
			repoManager.updateRepo(repoId);
		}
		catch (IOException ex) {
			log.warn("Repo update failed for {}: {}", repoId, ex.getMessage());
		}

		// Also add to helm CLI to ensure tests that depend on helm CLI (like
		// testSearchAndCompare) work
		try {
			log.info("Adding repo {} at {} to Helm CLI", repoId, repoUrl);
			new ProcessBuilder("helm", "repo", "add", repoId, repoUrl).start().waitFor();
			new ProcessBuilder("helm", "repo", "update", repoId).start().waitFor();
		}
		catch (Exception ex) {
			log.warn("Failed to add repo to Helm CLI: {}", ex.getMessage());
		}

		addedRepos.add(repoId);
	}

	private void fetchFromHelmRepo(String chartName) throws Exception {
		log.info("Fetching chart {} via RepoManager...", chartName);
		File tempDir = new File("target/temp-charts");
		tempDir.mkdirs();

		// chartName may be in the form <repoId>/<chartName>
		String repoId = null;
		String shortName = chartName;
		if (chartName.contains("/")) {
			int i = chartName.indexOf('/');
			repoId = chartName.substring(0, i);
			shortName = chartName.substring(i + 1);
		}

		// Use latest version from index
		try {
			List<RepoManager.ChartVersion> versions = repoManager.getChartVersions(repoId, shortName);
			if (versions.isEmpty()) {
				throw new IOException("No versions found for chart '" + shortName + "' in repo '" + repoId + "'");
			}
			String version = versions.getFirst().getChartVersion();
			repoManager.pull(chartName, repoId, version, tempDir.getAbsolutePath());
		}
		catch (IOException ex) {
			log.error("Failed to pull chart {}: {}", chartName, ex.getMessage());
			throw ex;
		}
	}

	private void fetchFromArtifactHub(String chartFullName) throws Exception {
		String repoNamePrefix = null;
		String chartNameOnly = chartFullName;
		if (chartFullName.contains("/")) {
			int slashIndex = chartFullName.indexOf("/");
			repoNamePrefix = chartFullName.substring(0, slashIndex);
			chartNameOnly = chartFullName.substring(slashIndex + 1);
		}

		// 1. Search for package
		String searchUrl = "https://artifacthub.io/api/v1/packages/search?ts_query_web=" + chartNameOnly + "&kind=0";
		JsonNode searchResult = callApi(searchUrl);
		JsonNode pkg = null;
		if (searchResult.has("packages") && searchResult.get("packages").isArray()) {
			for (JsonNode p : searchResult.get("packages")) {
				String pName = p.get("name").asString();
				String pRepo = p.get("repository").get("name").asString();

				if (pName.equals(chartNameOnly)) {
					if (repoNamePrefix == null || pRepo.equals(repoNamePrefix)) {
						pkg = p;
						break;
					}
				}
			}
			if (pkg == null && repoNamePrefix == null && !searchResult.get("packages").isEmpty()) {
				pkg = searchResult.get("packages").get(0);
			}
		}

		if (pkg == null) {
			throw new Exception("Package not found in Artifact Hub: " + chartFullName);
		}

		String repoName = pkg.get("repository").get("name").asString();
		String pkgName = pkg.get("name").asString();
		String version = pkg.get("version").asString();

		// 2. Get package details
		String detailUrl = "https://artifacthub.io/api/v1/packages/helm/" + repoName + "/" + pkgName + "/" + version;
		JsonNode details = callApi(detailUrl);
		String contentUrl = details.get("content_url").asString();

		// 3. Download and untar
		File testChartsDir = new File("target/test-charts");
		testChartsDir.mkdirs();
		String fileName = pkgName + ".tgz";
		repoManager.pullFromUrl(contentUrl, testChartsDir.getAbsolutePath(), fileName);
		repoManager.untar(new File(testChartsDir, fileName), testChartsDir);
	}

	private JsonNode callApi(String urlString) throws Exception {
		URL url = URI.create(urlString).toURL();
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
			sc.init(null, new TrustManager[] { new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public void checkClientTrusted(X509Certificate[] certs, String authType) {
				}

				public void checkServerTrusted(X509Certificate[] certs, String authType) {
				}
			} }, new SecureRandom());
			conn.setSSLSocketFactory(sc.getSocketFactory());
			conn.setHostnameVerifier((hostname, session) -> true);
		}
		catch (Exception ex) {
			log.error("Failed to setup insecure SSL in test", ex);
		}
	}

	/**
	 * Fetches the top N Helm charts from Artifact Hub sorted by relevance, returning them
	 * as JUnit {@link Arguments} of {@code (chartName, repoId, repoUrl)}. The count is
	 * controlled by the {@code jhelmtest.number-of-top-charts} property (default 30).
	 */
	Stream<Arguments> topCharts() throws Exception {
		int limit = testProperties.getNumberOfTopCharts();
		String url = "https://artifacthub.io/api/v1/packages/search?kind=0&sort=relevance&limit=" + limit;
		JsonMapper mapper = JsonMapper.builder().build();
		JsonNode result;
		try (InputStream in = URI.create(url).toURL().openStream()) {
			result = mapper.readTree(in);
		}
		JsonNode packages = result.has("packages") ? result.get("packages") : result;

		List<Arguments> args = new ArrayList<>();
		for (JsonNode pkg : packages) {
			String name = pkg.get("name").asString();
			JsonNode repo = pkg.get("repository");
			String repoId = repo.get("name").asString();
			String repoUrl = repo.get("url").asString();
			args.add(Arguments.of(repoId + "/" + name, repoId, repoUrl));
		}
		return args.stream();
	}

	@ParameterizedTest
	@MethodSource("topCharts")
	void compareTopCharts(String chartName, String repoId, String repoUrl) throws Exception {
		compareChart(chartName, "release-" + chartName, repoId, repoUrl);
	}

	record Diff(String path, String expected, String actual) {
		@Override
		public String toString() {
			return path + ": expected=" + expected + ", actual=" + actual;
		}
	}

	record IgnoreRule(String resource, String path, String reason) {
	}

}
