package org.alexmond.jhelm.core;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.config.JhelmTestProperties;
import org.alexmond.jhelm.core.config.KpsTestConfig;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.Engine;
import org.alexmond.jhelm.core.service.RepoManager;
import org.junit.jupiter.api.Tag;
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
import java.nio.file.Path;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Slf4j
@SpringBootTest
@ContextConfiguration(classes = KpsTestConfig.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KpsComparisonTest {

	private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();

	/**
	 * Repository URL overrides for charts whose upstream repo has moved but whose
	 * ArtifactHub metadata still advertises the old (now 404) location. Keyed by the
	 * stale URL, mapped to the working replacement.
	 */
	private static final Map<String, String> REPO_URL_OVERRIDES = Map.of(
			// kubernetes-dashboard: repo renamed to kubernetes-retired; the old
			// kubernetes.github.io/dashboard GitHub Pages site now returns 404 (#315)
			"https://kubernetes.github.io/dashboard", "https://kubernetes-retired.github.io/dashboard");

	@Autowired
	private JhelmTestProperties testProperties;

	private final RepoManager repoManager = createRepoManager();

	private final ChartLoader chartLoader = new ChartLoader();

	private final Engine engine = new Engine();

	private final InstallAction installAction = new InstallAction(engine, null);

	private final JsonMapper objectMapper = JsonMapper.builder().build();

	private final Set<String> addedRepos = new HashSet<>();

	private final Set<String> skipCharts = loadSkipCharts();

	private static Set<String> loadSkipCharts() {
		Set<String> skips = new HashSet<>();
		try (InputStream is = KpsComparisonTest.class.getResourceAsStream("/charts-skip.csv")) {
			if (is == null) {
				return skips;
			}
			new String(is.readAllBytes(), StandardCharsets.UTF_8).lines()
				.filter((line) -> !line.isBlank() && !line.startsWith("#"))
				.map((line) -> line.split(",")[0].trim())
				.forEach(skips::add);
		}
		catch (IOException ex) {
			// Ignore — no skip file
		}
		return skips;
	}

	private boolean isSkipped(String chartName) {
		return this.skipCharts.contains(chartName);
	}

	private RepoManager createRepoManager() {
		RepoManager rm = new RepoManager(null, true);
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

		Release release = installAction.install(InstallOptions.builder()
			.chart(chart)
			.releaseName("simple")
			.namespace("default")
			.values(Map.of())
			.revision(1)
			.dryRun(false)
			.build());
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
			compareChart("nginx", "pulled-nginx", "bitnami", "https://charts.bitnami.com/bitnami", null);
		}
	}

	@ParameterizedTest
	@CsvFileSource(resources = "/single.csv")
	void compareSingleChart(String chartName, String repoId, String repoUrl, String version) throws Exception {
		compareChart(chartName, "release-" + chartName, repoId, repoUrl, version);
	}

	@Tag("comparison")
	@ParameterizedTest
	@CsvFileSource(resources = "/charts.csv")
	void compareAllTopCharts(String chartName, String repoId, String repoUrl, String version) throws Exception {
		compareChart(chartName, "release-" + chartName, repoId, repoUrl, version);
	}

	/**
	 * Semi-autonomous chart-version upgrade bot (run on demand by the weekly
	 * upgrade-charts workflow, never in the normal build). For each pinned chart it
	 * resolves the latest upstream version and, if newer, renders parity at it: a version
	 * that still matches Helm byte-for-byte gets its pin bumped in charts.csv; one that
	 * diverges keeps its current pin and is reported for a human to investigate. Writes a
	 * markdown summary to target/upgrade-report.md (used as the PR body).
	 */
	@Tag("comparison")
	@Test
	void upgradePins() throws Exception {
		Path csv = Path.of("src/test/resources/charts.csv");
		List<String> lines = Files.readAllLines(csv);
		List<String> out = new ArrayList<>();
		List<String> bumped = new ArrayList<>();
		List<String> held = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (String line : lines) {
			String[] p = line.split(",", 4);
			if (line.isBlank() || line.startsWith("#") || p.length < 4) {
				out.add(line);
				continue;
			}
			String chart = p[0];
			String repoId = p[1];
			String repoUrl = p[2];
			String pinned = p[3];
			String newVersion = pinned;
			try {
				newVersion = tryUpgrade(chart, repoId, repoUrl, pinned, bumped, held, errors);
			}
			catch (Exception ex) {
				errors.add(chart + ": " + ex.getMessage());
			}
			out.add(String.join(",", chart, repoId, repoUrl, newVersion));
		}
		Files.writeString(csv, String.join("\n", out) + "\n");
		writeUpgradeReport(bumped, held, errors);
		log.info("upgradePins: bumped={} held={} errors={}", bumped.size(), held.size(), errors.size());
	}

	/**
	 * Resolves the latest version of one chart and bumps its pin if it still matches
	 * Helm.
	 */
	private String tryUpgrade(String chart, String repoId, String repoUrl, String pinned, List<String> bumped,
			List<String> held, List<String> errors) throws Exception {
		if (isSkipped(chart)) {
			return pinned;
		}
		String shortName = chart.contains("/") ? chart.substring(chart.indexOf('/') + 1) : chart;
		String effectiveRepoUrl = REPO_URL_OVERRIDES.getOrDefault(repoUrl.replaceAll("/+$", ""), repoUrl);
		addHelmRepo(repoId, effectiveRepoUrl);
		List<RepoManager.ChartVersion> versions = repoManager.getChartVersions(repoId, shortName);
		if (versions.isEmpty()) {
			errors.add(chart + ": no versions found");
			return pinned;
		}
		String latest = versions.getFirst().getChartVersion();
		if (latest.equals(pinned)) {
			return pinned;
		}
		String releaseName = sanitizeReleaseName("release-" + chart);
		Map<String, Object> overrides = testProperties.getComparisonValues().getOrDefault(chart, Map.of());
		fetchFromHelmRepo(chart, latest);
		File dir = findChartDir(chart, latest);
		if (dir == null) {
			errors.add(chart + " " + latest + ": fetch failed");
			return pinned;
		}
		String helm = runHelmInstallDryRun(dir, releaseName, "default", overrides);
		if (helm == null) {
			errors.add(chart + " " + latest + ": helm template failed");
			return pinned;
		}
		Chart loaded = chartLoader.load(dir);
		String jhelm = installAction
			.install(InstallOptions.builder()
				.chart(loaded)
				.releaseName(releaseName)
				.namespace("default")
				.values(overrides)
				.revision(1)
				.dryRun(true)
				.build())
			.getManifest();
		List<String> fails = computeManifestFailures(chart, jhelm, helm);
		if (fails.isEmpty()) {
			bumped.add(chart + ": " + pinned + " -> " + latest);
			return latest;
		}
		held.add(chart + ": " + pinned + " held (latest " + latest + " diverges in " + fails.size() + " resource(s))");
		return pinned;
	}

	private void writeUpgradeReport(List<String> bumped, List<String> held, List<String> errors) throws IOException {
		StringBuilder r = new StringBuilder();
		r.append("## Chart version upgrade\n\n");
		r.append("Bumped **")
			.append(bumped.size())
			.append("**, held **")
			.append(held.size())
			.append("** (diverge), errors **")
			.append(errors.size())
			.append("**.\n\n");
		appendSection(r, "Bumped — still byte-for-byte vs Helm at the new version", bumped);
		appendSection(r, "Held — newer version diverges, kept old pin (needs investigation)", held);
		appendSection(r, "Could not check (resolve/fetch/helm error)", errors);
		Files.writeString(Path.of("target/upgrade-report.md"), r.toString());
	}

	private void appendSection(StringBuilder r, String title, List<String> items) {
		if (items.isEmpty()) {
			return;
		}
		r.append("### ").append(title).append("\n\n");
		for (String item : items) {
			r.append("- ").append(item).append('\n');
		}
		r.append('\n');
	}

	private List<String[]> readFailedCsv() throws Exception {
		try (InputStream is = getClass().getResourceAsStream("/failed.csv")) {
			if (is == null) {
				return List.of();
			}
			String content = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
			if (content.isEmpty()) {
				return List.of();
			}
			return content.lines()
				.filter((line) -> !line.isBlank() && !line.startsWith("#"))
				.map((line) -> line.split(",", 3))
				.filter((parts) -> parts.length == 3)
				.toList();
		}
	}

	@Tag("comparison")
	@Test
	void compareFailedCharts() throws Exception {
		List<String[]> charts = readFailedCsv();
		if (charts.isEmpty()) {
			log.info("failed.csv is empty — no failed charts to verify");
			return;
		}
		List<String> unexpectedPasses = new ArrayList<>();
		for (String[] parts : charts) {
			String chartName = parts[0].trim();
			String repoId = parts[1].trim();
			String repoUrl = parts[2].trim();
			try {
				compareChart(chartName, "release-" + chartName, repoId, repoUrl, null);
				unexpectedPasses.add(chartName);
				log.warn("{} - UNEXPECTEDLY PASSED — remove from failed.csv", chartName);
			}
			catch (AssertionError ex) {
				log.info("{} - Expected failure confirmed: {}", chartName,
						ex.getMessage().lines().findFirst().orElse(ex.getMessage()));
			}
			catch (Exception ex) {
				log.info("{} - Expected failure (exception): {}", chartName, ex.getMessage());
			}
		}
		if (!unexpectedPasses.isEmpty()) {
			fail("Charts that unexpectedly PASSED (remove from failed.csv): " + unexpectedPasses);
		}
	}

	private void compareChart(String chartName, String releaseName, String repoId, String repoUrl, String version)
			throws Exception {
		if (isSkipped(chartName)) {
			log.info("{} - Skipped (listed in charts-skip.csv)", chartName);
			assumeTrue(false, chartName + " is in charts-skip.csv");
		}

		// Redirect charts whose upstream repo has moved but whose ArtifactHub metadata
		// still points at the old (now 404) location.
		String effectiveRepoUrl = REPO_URL_OVERRIDES.getOrDefault(repoUrl.replaceAll("/+$", ""), repoUrl);

		File chartDir = findChartDir(chartName, version);

		if (chartDir == null) {
			log.info("Chart {} not found locally, fetching from repository {}...", chartName, effectiveRepoUrl);
			try {
				addHelmRepo(repoId, effectiveRepoUrl);
				fetchFromHelmRepo(chartName, version);
				chartDir = findChartDir(chartName, version);
			}
			catch (Exception ex) {
				log.warn("Failed to fetch chart {} from repository: {}", chartName, ex.getMessage());
				assumeTrue(false, "Chart repo unavailable for " + chartName + ": " + ex.getMessage());
			}
		}

		assumeTrue(chartDir != null, "Chart " + chartName + " not found and could not be fetched");

		// Sanitize release name to be valid for Helm (alphanumeric and hyphens only, no
		// slashes)
		String sanitizedReleaseName = sanitizeReleaseName(releaseName);
		log.info("Using sanitized release name: {} (from {})", sanitizedReleaseName, releaseName);

		String sanitizedName = chartName.replace("/", "_");

		// Value overrides for charts that require mandatory values (passed to both Helm
		// and jhelm so any divergence is a real jhelm bug).
		Map<String, Object> overrideValues = testProperties.getComparisonValues().getOrDefault(chartName, Map.of());

		// STEP 1: Run Helm template FIRST and save output
		log.info("{} - Running Helm template first...", chartName);
		String helmManifest = runHelmInstallDryRun(chartDir, sanitizedReleaseName, "default", overrideValues);

		if (helmManifest == null) {
			// Helm itself failed — skip this chart (requires mandatory values, etc.)
			log.warn("{} - Helm template failed, skipping comparison", chartName);
			assumeTrue(false, chartName + " - Helm template command failed (chart requires mandatory values)");
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
			Release release = installAction.install(InstallOptions.builder()
				.chart(chart)
				.releaseName(sanitizedReleaseName)
				.namespace("default")
				.values(overrideValues)
				.revision(1)
				.dryRun(true)
				.build());
			assertNotNull(release);

			String jhelmManifest = release.getManifest();
			log.info("{} - JHelm manifest length: {}", chartName, jhelmManifest.length());

			File actualFile = new File("target/test-output/actual_" + sanitizedName + ".yaml");
			actualFile.getParentFile().mkdirs();
			Files.writeString(actualFile.toPath(), jhelmManifest);

			File expectedFile = new File("target/test-output/expected_" + sanitizedName + ".yaml");
			Files.writeString(expectedFile.toPath(), helmManifest);

			// Compare manifests
			compareManifests(chartName, repoId, repoUrl, jhelmManifest, helmManifest);

		}
		catch (Exception ex) {
			// If the chart has a catch-all ignore (resource: "*", path: "*"), treat
			// rendering failures as known bugs and skip instead of failing
			if (hasCatchAllIgnore(chartName)) {
				log.warn("{} - JHelm rendering failed (ignored — catch-all rule): {}", chartName, ex.getMessage());
				return;
			}
			// Build root-cause chain for clear error reporting
			String rootCause = extractRootCause(ex);
			log.error("{} - JHelm rendering failed: {}", chartName, rootCause);
			fail(chartName + " - JHelm rendering failed: " + rootCause + "\n  failed.csv: " + chartName + "," + repoId
					+ "," + repoUrl);
		}
	}

	private String runHelmInstallDryRun(File chartDir, String releaseName, String namespace,
			Map<String, Object> values) {
		try {
			// Pin the kube version so version-gated template logic matches jhelm's
			// Capabilities.KubeVersion (Engine sets v1.35.0). Otherwise the helm CLI's
			// built-in default kube version varies by helm build and charts gating on it
			// (e.g. Service.spec.trafficDistribution, hostUsers) diverge.
			List<String> command = new ArrayList<>(List.of("helm", "template", releaseName, chartDir.getAbsolutePath(),
					"--namespace", namespace, "--kube-version", "v1.35.0"));
			if (values != null && !values.isEmpty()) {
				File valuesFile = File.createTempFile("jhelm-values-", ".yaml");
				valuesFile.deleteOnExit();
				YAML_MAPPER.writeValue(valuesFile, values);
				command.add("--values");
				command.add(valuesFile.getAbsolutePath());
			}
			ProcessBuilder pb = new ProcessBuilder(command);
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

	private void compareManifests(String chartName, String repoId, String repoUrl, String jhelm, String helm)
			throws Exception {
		List<String> failures = computeManifestFailures(chartName, jhelm, helm);
		if (failures.isEmpty()) {
			log.info("{} - All resources match Helm output!", chartName);
			return;
		}
		StringBuilder msg = new StringBuilder();
		msg.append(chartName).append(" - ").append(failures.size()).append(" comparison failure(s):\n");
		for (String f : failures) {
			msg.append("  - ").append(f).append('\n');
		}
		msg.append("  failed.csv: ")
			.append(chartName)
			.append(',')
			.append(repoId)
			.append(',')
			.append(repoUrl)
			.append('\n');
		fail(msg.toString());
	}

	/**
	 * Renders the per-resource diff between jhelm and helm output and returns the list of
	 * un-ignored comparison failures (empty when they match). Shared by the asserting
	 * comparison and the version-upgrade bot, which needs the result without failing.
	 */
	private List<String> computeManifestFailures(String chartName, String jhelm, String helm) throws Exception {
		// Parse both manifests into YAML documents.
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
		extraInJhelm.removeIf((key) -> isIgnored(key, "*", ignoreRules));

		List<String> failures = new ArrayList<>();

		if (!missingInJhelm.isEmpty()) {
			failures.add("Resources missing in JHelm: " + missingInJhelm);
		}

		// Emitting a resource Helm does not is a real divergence — fail rather than warn
		// (symmetric with the missing-resource check; an explicitly ignored extra is
		// filtered out above).
		if (!extraInJhelm.isEmpty()) {
			failures.add("Extra resources in JHelm (not in Helm): " + extraInJhelm);
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

		return failures;
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
				// Tolerated on purpose: this crude "\n---\n" split can slice through a
				// YAML
				// block scalar (|-, >) whose content spans the separator, yielding a
				// fragment
				// that is not valid standalone YAML even though the manifest is fine.
				// This is
				// a harness limitation, not a jhelm rendering bug — it fires
				// symmetrically for
				// both helm and jhelm output — so skip the fragment rather than failing
				// the
				// chart on a false positive. (A real rendering divergence still surfaces
				// via
				// the missing/extra-resource and per-field diff checks.)
				log.warn("Skipping unparseable YAML fragment at position {} (block-scalar split): {}", i,
						ex.getMessage());
			}
		}

		return docs;
	}

	private Map<String, JsonNode> buildResourceMap(List<JsonNode> docs) {
		Map<String, JsonNode> map = new HashMap<>();

		for (JsonNode doc : docs) {
			addResourceToMap(map, doc);
		}

		return map;
	}

	private void addResourceToMap(Map<String, JsonNode> map, JsonNode doc) {
		String kind = doc.has("kind") ? doc.get("kind").asString() : "Unknown";

		// A `kind: List` document is just a container (Helm emits these, e.g. the
		// stakater storage charts). Expand it and key each item by its own kind/name —
		// otherwise multiple unnamed List documents all collide under "List/unnamed" and
		// get compared against each other, producing spurious diffs even when every
		// contained resource matches Helm byte-for-byte.
		if ("List".equals(kind) && doc.has("items") && doc.get("items").isArray()) {
			for (JsonNode item : doc.get("items")) {
				if (item != null && item.isObject()) {
					addResourceToMap(map, item);
				}
			}
			return;
		}

		String name = "unnamed";
		if (doc.has("metadata") && doc.get("metadata").has("name")) {
			name = doc.get("metadata").get("name").asString();
		}

		map.put(kind + "/" + name, doc);
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
			if (expected.isString() && actual.isString()) {
				try {
					JsonNode parsedExpected = YAML_MAPPER.readTree(expected.stringValue());
					JsonNode parsedActual = YAML_MAPPER.readTree(actual.stringValue());
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

	private boolean hasCatchAllIgnore(String chartName) {
		List<IgnoreRule> rules = loadIgnoreRules(chartName);
		for (IgnoreRule rule : rules) {
			if ("*".equals(rule.resource()) && "*".equals(rule.path())) {
				return true;
			}
		}
		return false;
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
		// Glob form: a pattern containing "[*]" matches array indices anywhere in the
		// path (e.g. "spec...containers[*].env[*].value"). Each "*" matches a run of
		// characters; the rest is matched literally. Used to ignore a per-index field
		// without listing every index. Backward-compatible: existing patterns never
		// contain "[*]", so they keep the prefix/trailing behavior below.
		if (pattern.contains("[*]")) {
			return value.matches(globToRegex(pattern));
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

	/**
	 * Converts a glob (where {@code *} matches any run of chars) to a full-match regex.
	 */
	private String globToRegex(String glob) {
		StringBuilder sb = new StringBuilder(glob.length() * 2);
		for (int i = 0; i < glob.length(); i++) {
			char c = glob.charAt(i);
			if (c == '*') {
				sb.append(".*");
			}
			else if ("\\.[]{}()+-^$|?".indexOf(c) >= 0) {
				sb.append('\\').append(c);
			}
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * Sanitize a release name to Helm's rules (alphanumeric/hyphen, leading char,
	 * &le;53).
	 */
	private String sanitizeReleaseName(String releaseName) {
		String name = releaseName.replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
		if (name.startsWith("-")) {
			name = "r" + name;
		}
		if (name.length() > 53) {
			name = name.substring(0, 53);
		}
		if (name.endsWith("-")) {
			name = name.substring(0, name.length() - 1);
		}
		return name;
	}

	private File findChartDir(String chartFullName, String version) {
		String chartName = chartFullName.contains("/") ? chartFullName.substring(chartFullName.lastIndexOf("/") + 1)
				: chartFullName;
		String sanitized = chartFullName.replace("/", "_");
		// When a version is pinned, the chart lives in a version-scoped dir so a re-pin
		// (e.g. the weekly upgrade bot) re-fetches instead of reusing a stale copy.
		if (version != null && !version.isBlank()) {
			File pinned = new File("target/temp-charts/" + sanitized + "@" + version + "/" + chartName);
			if (pinned.isDirectory() && new File(pinned, "Chart.yaml").exists()) {
				return pinned;
			}
			return null;
		}
		// Per-chart subdirectory first (isolated), then legacy fallbacks
		String[] paths = { "target/temp-charts/" + sanitized + "/" + chartName, "target/temp-charts/" + chartName,
				"target/temp-charts/" + chartFullName, "sample-charts/" + chartName, "../sample-charts/" + chartName,
				"target/test-charts/" + chartName, "target/test-charts/bitnami/" + chartName };
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

	private void fetchFromHelmRepo(String chartName, String pinnedVersion) throws Exception {
		log.info("Fetching chart {} (version {}) via RepoManager...", chartName,
				(pinnedVersion == null || pinnedVersion.isBlank()) ? "latest" : pinnedVersion);
		// Use a per-chart subdirectory to avoid cross-contamination between test
		// iterations; version-scoped when a version is pinned (see findChartDir).
		String sanitized = chartName.replace("/", "_");
		boolean pinned = pinnedVersion != null && !pinnedVersion.isBlank();
		File tempDir = new File("target/temp-charts/" + sanitized + (pinned ? "@" + pinnedVersion : ""));
		if (tempDir.exists()) {
			deleteDir(tempDir);
		}
		tempDir.mkdirs();

		// chartName may be in the form <repoId>/<chartName>
		String repoId = null;
		String shortName = chartName;
		if (chartName.contains("/")) {
			int i = chartName.indexOf('/');
			repoId = chartName.substring(0, i);
			shortName = chartName.substring(i + 1);
		}

		// Pull the pinned version when given, else the latest from the index.
		try {
			String version = pinnedVersion;
			if (version == null || version.isBlank()) {
				List<RepoManager.ChartVersion> versions = repoManager.getChartVersions(repoId, shortName);
				if (versions.isEmpty()) {
					throw new IOException("No versions found for chart '" + shortName + "' in repo '" + repoId + "'");
				}
				version = versions.getFirst().getChartVersion();
			}
			repoManager.pull(chartName, repoId, version, tempDir.getAbsolutePath());
		}
		catch (IOException ex) {
			log.error("Failed to pull chart {}: {}", chartName, ex.getMessage());
			throw ex;
		}
	}

	private static void deleteDir(File dir) {
		File[] files = dir.listFiles();
		if (files != null) {
			for (File f : files) {
				if (f.isDirectory()) {
					deleteDir(f);
				}
				else {
					f.delete();
				}
			}
		}
		dir.delete();
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
		int total = testProperties.getNumberOfTopCharts();
		JsonMapper mapper = JsonMapper.builder().build();
		List<Arguments> args = new ArrayList<>();

		int offset = 0;
		while (args.size() < total) {
			int batchSize = Math.min(60, total - args.size());
			String url = "https://artifacthub.io/api/v1/packages/search?kind=0&sort=relevance&limit=" + batchSize
					+ "&offset=" + offset;
			HttpsURLConnection conn = (HttpsURLConnection) URI.create(url).toURL().openConnection();
			setupInsecureSsl(conn);
			JsonNode result;
			try (InputStream in = conn.getInputStream()) {
				result = mapper.readTree(in);
			}
			JsonNode packages = result.has("packages") ? result.get("packages") : result;
			if (packages.isEmpty()) {
				break;
			}
			for (JsonNode pkg : packages) {
				String name = pkg.get("name").asString();
				JsonNode repo = pkg.get("repository");
				String repoId = repo.get("name").asString();
				String repoUrl = repo.get("url").asString();
				args.add(Arguments.of(repoId + "/" + name, repoId, repoUrl));
			}
			offset += batchSize;
		}
		return args.stream();
	}

	@Tag("comparison")
	@ParameterizedTest
	@MethodSource("topCharts")
	void compareTopCharts(String chartName, String repoId, String repoUrl) throws Exception {
		compareChart(chartName, "release-" + chartName, repoId, repoUrl, null);
	}

	private static String extractRootCause(Throwable ex) {
		StringBuilder sb = new StringBuilder();
		Throwable current = ex;
		while (current != null) {
			if (sb.length() > 0) {
				sb.append(" -> ");
			}
			String msg = current.getMessage();
			if (msg != null && !msg.isBlank()) {
				sb.append(msg.lines().findFirst().orElse(msg));
			}
			else {
				sb.append(current.getClass().getSimpleName());
			}
			current = current.getCause();
		}
		return sb.toString();
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
