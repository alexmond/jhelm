package org.alexmond.jhelm.core.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.cache.TemplateCache;
import org.alexmond.jhelm.core.exception.SchemaValidationException;
import org.alexmond.jhelm.core.exception.TemplateRenderException;
import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.gotmpl4j.Function;
import org.alexmond.gotmpl4j.GoTemplate;
import org.alexmond.gotmpl4j.GoTemplateRegistry;
import org.alexmond.gotmpl4j.parse.Node;
import org.alexmond.jhelm.gotemplate.helm.functions.KubernetesFunctions;
import org.alexmond.jhelm.gotemplate.helm.functions.KubernetesProvider;

import java.io.StringWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.alexmond.jhelm.core.model.Capabilities;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartFiles;
import org.alexmond.jhelm.core.model.Dependency;
import org.alexmond.jhelm.core.model.ReleaseContext;
import org.alexmond.jhelm.core.model.Values;
import org.alexmond.jhelm.core.model.VersionSet;

/**
 * Renders a Helm {@link Chart} to Kubernetes manifests by driving the Go template engine.
 * It assembles the rendering context ({@code .Values}, {@code .Release}, {@code .Chart},
 * {@code .Capabilities}, {@code .Files}), resolves named templates and subchart
 * dependencies, and validates values against the chart's JSON schema. Optionally backed
 * by a {@link TemplateCache} for parse reuse and a {@link JhelmMetrics} for
 * instrumentation.
 */
@Slf4j
public class Engine {

	// Exactly Helm's built-in chartutil.DefaultVersionSet — the .Capabilities.APIVersions
	// that `helm template` exposes with no live cluster (dumped from helm 3.x). Matching
	// it
	// verbatim keeps `.Capabilities.APIVersions.Has` checks in parity with Helm; notably
	// it
	// excludes apiregistration.k8s.io/* and keeps legacy *beta1 groups (e.g. extensions/
	// v1beta1, apps/v1beta1, autoscaling/v2beta1) that a newer client-go scheme drops.
	private static final List<String> DEFAULT_API_VERSIONS = List.of("admissionregistration.k8s.io/v1",
			"admissionregistration.k8s.io/v1alpha1", "admissionregistration.k8s.io/v1beta1", "apiextensions.k8s.io/v1",
			"apiextensions.k8s.io/v1beta1", "apps/v1", "apps/v1beta1", "apps/v1beta2", "authentication.k8s.io/v1",
			"authentication.k8s.io/v1alpha1", "authentication.k8s.io/v1beta1", "authorization.k8s.io/v1",
			"authorization.k8s.io/v1beta1", "autoscaling/v1", "autoscaling/v2", "autoscaling/v2beta1",
			"autoscaling/v2beta2", "batch/v1", "batch/v1beta1", "certificates.k8s.io/v1",
			"certificates.k8s.io/v1alpha1", "certificates.k8s.io/v1beta1", "coordination.k8s.io/v1",
			"coordination.k8s.io/v1alpha2", "coordination.k8s.io/v1beta1", "discovery.k8s.io/v1",
			"discovery.k8s.io/v1beta1", "events.k8s.io/v1", "events.k8s.io/v1beta1", "extensions/v1beta1",
			"flowcontrol.apiserver.k8s.io/v1", "flowcontrol.apiserver.k8s.io/v1beta1",
			"flowcontrol.apiserver.k8s.io/v1beta2", "flowcontrol.apiserver.k8s.io/v1beta3",
			"internal.apiserver.k8s.io/v1alpha1", "networking.k8s.io/v1", "networking.k8s.io/v1beta1", "node.k8s.io/v1",
			"node.k8s.io/v1alpha1", "node.k8s.io/v1beta1", "policy/v1", "policy/v1beta1",
			"rbac.authorization.k8s.io/v1", "rbac.authorization.k8s.io/v1alpha1", "rbac.authorization.k8s.io/v1beta1",
			"resource.k8s.io/v1", "resource.k8s.io/v1alpha3", "resource.k8s.io/v1beta1", "resource.k8s.io/v1beta2",
			"scheduling.k8s.io/v1", "scheduling.k8s.io/v1alpha1", "scheduling.k8s.io/v1beta1", "storage.k8s.io/v1",
			"storage.k8s.io/v1alpha1", "storage.k8s.io/v1beta1", "storagemigration.k8s.io/v1alpha1", "v1");

	// Default .Capabilities.KubeVersion when no live cluster / override is supplied. Kept
	// in sync with the helm --kube-version the parity harness (KpsComparisonTest) pins,
	// so
	// offline `template` output matches upstream helm.
	private static final String DEFAULT_KUBE_VERSION = "v1.35.0";

	private final Map<String, String> namedTemplates = new HashMap<>();

	private final Map<String, String> templateVersions = new HashMap<>();

	private final TemplateCache templateCache;

	private final SchemaValidator schemaValidator;

	private final JhelmMetrics metrics;

	private GoTemplate factory;

	// Shared function registry, built once per engine. It runs the ServiceLoader
	// discovery
	// and builds the template-INDEPENDENT function set (Sprig's ~260 functions) and the
	// reflection/dispatch caches a single time, then every per-render GoTemplate is built
	// from it (see doRender) so those are reused instead of rebuilt each render. The
	// template-DEPENDENT Helm provider (include/tpl/required, which close over the
	// specific
	// template) is re-bound per render instance by the registry, so rendering is
	// unchanged.
	private final GoTemplateRegistry templateRegistry = GoTemplateRegistry.create();

	// Optional cluster-backed provider for the `lookup` template function. Null when no
	// Kubernetes access is wired (e.g. jhelm-core used standalone) — lookup then falls
	// back
	// to the ServiceLoader stub that returns an empty map. Set by the kube autoconfig.
	private KubernetesProvider kubernetesProvider;

	// Per-render record of the text hash last parsed under each template name, so the
	// render pass can skip re-parsing a template the collect pass already parsed (see
	// parseWithCache). Reset each render in doRender alongside the factory.
	private final Map<String, Integer> parsedTextHash = new HashMap<>();

	/**
	 * Creates an engine with no template cache and no metrics, using a default schema
	 * validator.
	 */
	public Engine() {
		this(null, new SchemaValidator(), null);
	}

	/**
	 * Creates an engine with the given template cache and schema validator and no
	 * metrics.
	 * @param templateCache the parse cache to reuse compiled templates, or {@code null}
	 * to disable caching
	 * @param schemaValidator validates values against a chart's JSON schema, or
	 * {@code null} for a default validator
	 */
	public Engine(TemplateCache templateCache, SchemaValidator schemaValidator) {
		this(templateCache, schemaValidator, null);
	}

	/**
	 * Creates an engine with the given template cache, schema validator and metrics.
	 * @param templateCache the parse cache to reuse compiled templates, or {@code null}
	 * to disable caching
	 * @param schemaValidator validates values against a chart's JSON schema, or
	 * {@code null} for a default validator
	 * @param metrics records render and cache metrics, or {@code null} to disable
	 * instrumentation
	 */
	public Engine(TemplateCache templateCache, SchemaValidator schemaValidator, JhelmMetrics metrics) {
		this.templateCache = templateCache;
		this.schemaValidator = (schemaValidator != null) ? schemaValidator : new SchemaValidator();
		this.metrics = metrics;
	}

	/**
	 * Wires the cluster-backed provider used by the {@code lookup} template function so
	 * it queries the live Kubernetes API (as Helm does during install/upgrade) instead of
	 * the ServiceLoader stub. Pass {@code null} to keep the stub (offline rendering). The
	 * provider degrades gracefully on its own when the API is unreachable, so it is safe
	 * to set even for offline {@code template} rendering.
	 * @param kubernetesProvider the live Kubernetes provider, or {@code null} for the
	 * stub
	 */
	public void setKubernetesProvider(KubernetesProvider kubernetesProvider) {
		this.kubernetesProvider = kubernetesProvider;
	}

	private void parseWithCache(String name, String text) {
		// The collect pass (collectNamedTemplates) parses every template into the factory
		// under its helm-style key; the render pass then re-parses the same (name, text)
		// before executing it — the single hottest render path. When the identical text
		// is
		// already parsed under this name AND it declares no `define` block, the re-parse
		// only
		// re-creates the identical root node, so skip it. Templates that declare `define`
		// (which could re-order the global define table) and key collisions (distinct
		// text
		// under the same name) still parse, preserving define precedence and collision
		// resolution. The `define` check is a cheap superset guard: a false positive only
		// costs a redundant parse, never correctness.
		Integer prevHash = parsedTextHash.get(name);
		if (prevHash != null && prevHash == text.hashCode() && !text.contains("define")
				&& factory.getRootNodes().containsKey(name)) {
			return;
		}
		if (templateCache == null) {
			factory.parse(name, text);
			parsedTextHash.put(name, text.hashCode());
			return;
		}
		String cacheKey = name + "|" + text.hashCode();
		Map<String, Node> cached = templateCache.get(cacheKey);
		if (cached != null) {
			factory.getRootNodes().putAll(cached);
			return;
		}
		Map<String, Node> before = new LinkedHashMap<>(factory.getRootNodes());
		factory.parse(name, text);
		Map<String, Node> after = factory.getRootNodes();
		Map<String, Node> added = new LinkedHashMap<>();
		for (Map.Entry<String, Node> entry : after.entrySet()) {
			if (!before.containsKey(entry.getKey())) {
				added.put(entry.getKey(), entry.getValue());
			}
		}
		templateCache.put(cacheKey, added);
		parsedTextHash.put(name, text.hashCode());
	}

	/**
	 * Stack size for the render thread. Deeply nested {@code tpl}/{@code include} chains
	 * — e.g. grafana/loki's {@code configMapOrSecretContentHash} →
	 * {@code calculatedConfig} → nested {@code tpl} — recurse far deeper on the JVM (each
	 * {@code tpl} builds a fresh template) than on Go's growable goroutine stacks,
	 * overflowing the default ~512KB stack. A large stack matches Go's behaviour for
	 * finite-but-deep nesting.
	 */
	private static final long RENDER_STACK_SIZE = 32L * 1024 * 1024;

	/**
	 * Renders the chart's templates to a single concatenated manifest string. Rendering
	 * runs on a dedicated thread with an enlarged stack to accommodate deeply nested
	 * {@code tpl}/{@code include} chains.
	 * @param chart the chart to render
	 * @param values the user-supplied values merged over the chart defaults, exposed as
	 * {@code .Values}
	 * @param release the release context exposed as {@code .Release} (name, namespace,
	 * revision, etc.)
	 * @return the rendered manifests joined into one document
	 * @throws TemplateRenderException if a template fails to parse or execute
	 * @throws SchemaValidationException if the values violate the chart's JSON schema
	 */
	@SneakyThrows
	public String render(Chart chart, Map<String, Object> values, ReleaseContext release) {
		return render(chart, values, release, Capabilities.DEFAULT);
	}

	/**
	 * Renders a chart with an explicit {@code .Capabilities} override.
	 * @param chart the chart to render
	 * @param values the merged values for this render
	 * @param release the release context ({@code .Release})
	 * @param capabilities the {@code .Capabilities} override (kube version + extra API
	 * versions); use {@link Capabilities#DEFAULT} for the engine built-ins
	 * @return the rendered manifest
	 */
	@SneakyThrows
	public String render(Chart chart, Map<String, Object> values, ReleaseContext release, Capabilities capabilities) {
		Map<String, Object> releaseInfo = release.toMap();
		Capabilities caps = (capabilities != null) ? capabilities : Capabilities.DEFAULT;
		long startNanos = System.nanoTime();
		try {
			AtomicReference<String> result = new AtomicReference<>();
			AtomicReference<RuntimeException> error = new AtomicReference<>();
			Thread renderThread = new Thread(null, () -> {
				try {
					result.set(doRender(chart, values, releaseInfo, caps));
				}
				catch (RuntimeException ex) {
					error.set(ex);
				}
			}, "jhelm-render", RENDER_STACK_SIZE);
			renderThread.start();
			renderThread.join();
			if (error.get() != null) {
				throw error.get();
			}
			return result.get();
		}
		finally {
			if (metrics != null) {
				metrics.recordRender(System.nanoTime() - startNanos);
			}
		}
	}

	private String doRender(Chart chart, Map<String, Object> values, Map<String, Object> releaseInfo,
			Capabilities capabilities) {
		namedTemplates.clear();
		templateVersions.clear();
		parsedTextHash.clear();
		// Build a fresh template per render (its parsed-node namespace is per-render),
		// but
		// from the shared registry so the ServiceLoader discovery, Sprig's function set,
		// and
		// the reflection/dispatch caches are reused instead of rebuilt each render. Helm
		// renders a nil/absent value as "" (missingkey=zero), not Go's "<no value>". When
		// a
		// live Kubernetes provider is wired, override the stub `lookup` with the
		// cluster-backed one so `lookup` returns real resources (incl. Secret/ConfigMap
		// data)
		// during install/upgrade, as Helm does — withFunctions is applied on top of the
		// registry-supplied providers, so the override wins.
		GoTemplate.Builder builder = GoTemplate.builder().registry(this.templateRegistry);
		if (this.kubernetesProvider != null) {
			Map<String, Function> overrides = new HashMap<>();
			overrides.put("lookup", KubernetesFunctions.getFunctions(this.kubernetesProvider).get("lookup"));
			builder.withFunctions(overrides);
		}
		this.factory = builder.build().option("missingkey=zero");

		// Apply aliases from dependency metadata before collecting templates, so that
		// subchart .Chart.Name and template registration keys use the alias consistently.
		applyAliasesFromMetadata(chart);

		// Collect all named templates (define blocks) first. Pass the render values so
		// templates from condition-disabled subcharts are pruned (as Helm does) and don't
		// pollute the global define namespace.
		collectNamedTemplates(chart, values);

		try {
			// Using a shared set for the whole rendering process to avoid redundant work
			// and loops
			Set<String> renderedCharts = new HashSet<>();
			String rendered = renderWithSubcharts(chart, values, releaseInfo, renderedCharts, 0, capabilities);
			return cleanManifest(rendered);
		}
		catch (StackOverflowError ex) {
			// Fail loudly instead of returning an error string as the manifest: a
			// recursive
			// template would otherwise render "successfully" with garbage that could
			// reach a
			// cluster. The render thread captures this RuntimeException and rethrows it.
			String chartName = chart.getMetadata().getName();
			throw new TemplateRenderException("recursive template inclusion or too deep nesting while rendering chart '"
					+ chartName + "'; check for circular {{ template }} calls", ex, chartName, null);
		}
	}

	/**
	 * Builds the {@code .Capabilities} object for the render context. Uses the supplied
	 * kube-version override when present (else {@link #DEFAULT_KUBE_VERSION}) and
	 * advertises the built-in {@link #DEFAULT_API_VERSIONS} plus any extra API versions
	 * from the override.
	 * @param capabilities the override (never {@code null}; use
	 * {@link Capabilities#DEFAULT})
	 * @return the {@code .Capabilities} map exposed to templates
	 */
	private Map<String, Object> buildCapabilities(Capabilities capabilities) {
		String kubeVersion = (capabilities.kubeVersion() != null && !capabilities.kubeVersion().isBlank())
				? normalizeKubeVersion(capabilities.kubeVersion()) : DEFAULT_KUBE_VERSION;
		String[] majorMinor = parseMajorMinor(kubeVersion);
		List<String> apiVersions = new ArrayList<>(DEFAULT_API_VERSIONS);
		// extraApiVersions is guaranteed non-null with non-null elements by Capabilities
		for (String extra : capabilities.extraApiVersions()) {
			if (!extra.isBlank() && !apiVersions.contains(extra)) {
				apiVersions.add(extra);
			}
		}
		return Map.of("KubeVersion",
				Map.of("Version", kubeVersion, "Major", majorMinor[0], "Minor", majorMinor[1], "GitVersion",
						kubeVersion),
				"HelmVersion", Map.of("Version", "v3.16.0", "GitCommit", "", "GitTreeState", "", "GoVersion", ""),
				"APIVersions", new VersionSet(apiVersions));
	}

	/**
	 * Normalises a user/cluster kube-version string to the Helm {@code vX.Y.Z} form,
	 * adding a leading {@code v} when absent.
	 * @param version the raw version (e.g. {@code 1.29}, {@code v1.29.0})
	 * @return the normalised version string
	 */
	private static String normalizeKubeVersion(String version) {
		String trimmed = version.trim();
		return trimmed.startsWith("v") ? trimmed : "v" + trimmed;
	}

	/**
	 * Extracts the {@code Major}/{@code Minor} fields from a kube-version string, keeping
	 * only the leading digits of each segment (a cluster may report a minor like
	 * {@code "35+"}).
	 * @param version a normalised kube-version (e.g. {@code v1.35.0})
	 * @return a two-element array {@code [major, minor]}
	 */
	private static String[] parseMajorMinor(String version) {
		String stripped = version.startsWith("v") ? version.substring(1) : version;
		String[] parts = stripped.split("\\.");
		String major = (parts.length > 0) ? leadingDigits(parts[0]) : "0";
		String minor = (parts.length > 1) ? leadingDigits(parts[1]) : "0";
		return new String[] { major.isEmpty() ? "0" : major, minor.isEmpty() ? "0" : minor };
	}

	private static String leadingDigits(String value) {
		StringBuilder digits = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (ch < '0' || ch > '9') {
				break;
			}
			digits.append(ch);
		}
		return digits.toString();
	}

	/**
	 * Clean up the manifest by removing empty YAML documents and trailing separators
	 */
	private String cleanManifest(String manifest) {
		if (manifest == null || manifest.isEmpty()) {
			return manifest;
		}

		String normalized = manifest.trim();

		// Split on YAML document separators the way Helm does. Helm's splitter
		// (regexp "(?:^|\\s*\\n)---\\s*") treats any line that begins with "---" as a
		// separator and consumes the trailing whitespace, so an inline "--- # comment"
		// (e.g. yugabyte's templates/secrets.yaml) is a separator whose comment becomes
		// the next document's content — the preceding resource is terminated cleanly.
		// Splitting only on a "---" alone on its own line glued such inline separators
		// onto the previous document (producing "------ # comment"), corrupting it so it
		// no longer parsed as a resource. The "---" must be at column 0; an indented
		// "---" inside a block scalar is content, not a separator (Helm behaves the
		// same).
		String[] docs = normalized.split("(?m)^---[ \\t]*");
		StringBuilder cleaned = new StringBuilder();

		for (String doc : docs) {
			String trimmed = doc.strip();
			// Skip empty documents and comment-only documents (no resource content).
			if (trimmed.isEmpty() || isCommentOnly(trimmed)) {
				continue;
			}
			if (cleaned.length() > 0) {
				cleaned.append("\n---\n");
			}
			cleaned.append(trimmed);
		}

		// Add final newline if there's content
		if (cleaned.length() > 0) {
			cleaned.append('\n');
		}

		return cleaned.toString();
	}

	/**
	 * @return {@code true} if every non-blank line of the document is a YAML comment, so
	 * the document carries no resource content and can be dropped
	 */
	private boolean isCommentOnly(String doc) {
		for (String line : doc.split("\n")) {
			String stripped = line.strip();
			if (!stripped.isEmpty() && !stripped.startsWith("#")) {
				return false;
			}
		}
		return true;
	}

	private void collectNamedTemplates(Chart chart, Map<String, Object> values) {
		// First pass: collect all templates without parsing, to have them available for
		// definitions
		// Also track which chart each template belongs to for proper namespacing
		Map<String, String> allTemplates = new HashMap<>();
		Map<String, String> templateToChartName = new HashMap<>();
		// Full umbrella-relative path per template (e.g.
		// "gitlab/charts/openbao/templates/
		// x.tpl"). Helm keys templates by this path and parses in its sorted order, so
		// when
		// two subcharts define the same named template the last one in that order wins.
		// Parsing in the same order makes jhelm's define resolution match Helm's.
		Map<String, String> templateFullPath = new HashMap<>();
		collectAllTemplates(chart, allTemplates, templateToChartName, templateFullPath, chart.getMetadata().getName(),
				values);

		// Second pass: parse each template. Helper files (.tpl) are parsed first so their
		// defines are present; within each group templates are parsed in Helm's full-path
		// order so same-named defines resolve to the same winner as Helm.
		List<String> sortedNames = allTemplates.keySet().stream().sorted((a, b) -> {
			boolean aTpl = a.endsWith(".tpl");
			boolean bTpl = b.endsWith(".tpl");
			if (aTpl && !bTpl) {
				return -1;
			}
			if (!aTpl && bTpl) {
				return 1;
			}
			// Distinct-content collision extras (synthetic "name@version/templates/..."
			// keys; chart names never contain '@') parse before the deduped primaries so
			// that the primary — the higher-version winner — wins a same-name define
			// conflict between versions of the same library chart (e.g. bitnami common).
			boolean aExtra = a.indexOf('@') >= 0;
			boolean bExtra = b.indexOf('@') >= 0;
			if (aExtra != bExtra) {
				return aExtra ? -1 : 1;
			}
			// Reproduce Helm's same-name define winner (last Parse wins). Two rules,
			// both verified against `helm template`:
			// * Across directories (parent templates/ vs subchart charts/<x>/templates/):
			// ascending directory order, so the parent's define is parsed last and wins
			// (e.g. gitlab umbrella vs openbao; define-order regression #21).
			// * Within one directory: the alphabetically SMALLER filename wins, so parse
			// filenames in DESCENDING order (the smaller is parsed last). This is how
			// one chart shipping two files for the same template resolves — e.g. redpanda
			// console's _console.serviceaccount.tpl beats the stale
			// _serviceaccount.go.tpl.
			// Directory-then-reverse-filename is a proper total order (transitive).
			String aPath = templateFullPath.getOrDefault(a, a);
			String bPath = templateFullPath.getOrDefault(b, b);
			String aDir = aPath.substring(0, aPath.lastIndexOf('/') + 1);
			String bDir = bPath.substring(0, bPath.lastIndexOf('/') + 1);
			int dirCmp = aDir.compareTo(bDir);
			return (dirCmp != 0) ? dirCmp : bPath.compareTo(aPath);
		}).toList();

		for (String name : sortedNames) {
			try {
				String chartName = templateToChartName.get(name);
				// Only helper files contribute named templates that may need a
				// chart-prefixed alias. Snapshot the keys beforehand so we can alias
				// exactly the defines this file adds — and nothing else.
				boolean mayDefine = name.contains("_helpers") || name.endsWith(".tpl");
				Set<String> beforeKeys = mayDefine ? new HashSet<>(factory.getRootNodes().keySet()) : null;
				parseWithCache(name, allTemplates.get(name));
				if (log.isDebugEnabled()) {
					log.debug("Parsed template: {} (from chart: {})", name, chartName);
				}
				if (beforeKeys != null) {
					createChartPrefixedAliases(chartName, beforeKeys);
				}
			}
			catch (Exception ex) {
				if (log.isWarnEnabled()) {
					log.warn("Parse failure for template '{}' in chart '{}': {}", name, templateToChartName.get(name),
							ex.getMessage());
				}
			}
		}

		// Count how many named templates (defines) we actually collected
		int namedTemplateCount = factory.getRootNodes().size() - allTemplates.size();
		if (log.isDebugEnabled()) {
			log.debug("Collected {} named templates from {} files", namedTemplateCount, allTemplates.size());
		}
	}

	/**
	 * Register chart-prefixed aliases ({@code <chartName>.<template>}) for the named
	 * templates added by the helper file just parsed. Only the keys absent from
	 * {@code beforeKeys} are aliased, and each gets a single prefix — re-aliasing the
	 * whole template set on every helper file would compound prefixes across subcharts
	 * and explode the node map (#311).
	 * @param chartName the chart whose helper file was just parsed
	 * @param beforeKeys the {@code rootNodes} key set captured before parsing that file
	 */
	private void createChartPrefixedAliases(String chartName, Set<String> beforeKeys) {
		Map<String, Node> rootNodes = factory.getRootNodes();
		List<String> newlyAdded = new ArrayList<>();
		for (String key : rootNodes.keySet()) {
			// Only newly added named templates (defines) — skip file-path keys.
			if (!beforeKeys.contains(key) && !key.contains("/") && !key.contains("\\")) {
				newlyAdded.add(key);
			}
		}

		for (String templateName : newlyAdded) {
			if (!templateName.startsWith(chartName + ".")) {
				String prefixedName = chartName + "." + templateName;
				Node node = rootNodes.get(templateName);
				if (node != null && !rootNodes.containsKey(prefixedName)) {
					rootNodes.put(prefixedName, node);
					if (log.isDebugEnabled()) {
						log.debug("Created template alias: {} -> {}", prefixedName, templateName);
					}
				}
			}
		}
	}

	private void collectAllTemplates(Chart chart, Map<String, String> templates,
			Map<String, String> templateToChartName, Map<String, String> templateFullPath, String rootChartName,
			Map<String, Object> values) {
		collectAllTemplates(chart, templates, templateToChartName, templateFullPath, rootChartName, rootChartName,
				values);
	}

	private void collectAllTemplates(Chart chart, Map<String, String> templates,
			Map<String, String> templateToChartName, Map<String, String> templateFullPath, String rootChartName,
			String pathPrefix, Map<String, Object> inheritedValues) {
		String chartName = chart.getMetadata().getName();
		String chartVersion = chart.getMetadata().getVersion();

		for (Chart.Template t : chart.getTemplates()) {
			// Use Helm-style path (chartName/templates/fileName) to match the names
			// that charts use with $.Template.BasePath in include calls
			String helmStyleKey = chartName + "/templates/" + t.getName();
			// Full umbrella-relative path, used only to order parsing like Helm.
			String fullPath = pathPrefix + "/templates/" + t.getName();
			// On collision, keep the template from the higher chart version. Newer
			// library chart versions are backward-compatible supersets of older ones,
			// so keeping the newest avoids missing-feature failures.
			String existingVersion = templateVersions.get(helmStyleKey);
			if (existingVersion == null || compareVersions(chartVersion, existingVersion) > 0) {
				// A genuinely different file is being displaced (not just an older copy
				// of the same library helper): keep its defines so they aren't lost.
				String displaced = templates.get(helmStyleKey);
				if (displaced != null && !displaced.equals(t.getData())) {
					preserveDistinctTemplate(templates, templateToChartName, templateFullPath,
							templateToChartName.get(helmStyleKey), templateFullPath.get(helmStyleKey), existingVersion,
							t.getName(), displaced);
				}
				templates.put(helmStyleKey, t.getData());
				templateToChartName.put(helmStyleKey, chartName);
				templateFullPath.put(helmStyleKey, fullPath);
				templateVersions.put(helmStyleKey, chartVersion);
			}
			else {
				// Same short key, equal/older version. If the content genuinely differs,
				// this is a distinct chart that merely shares a name (e.g. an umbrella
				// and a subchart both named "gitlab"): keep its defines under a version-
				// qualified key instead of dropping the whole file (#18).
				String kept = templates.get(helmStyleKey);
				if (kept != null && !kept.equals(t.getData())) {
					preserveDistinctTemplate(templates, templateToChartName, templateFullPath, chartName, fullPath,
							chartVersion, t.getName(), t.getData());
				}
			}
		}

		// Merged values at this level, used to evaluate subchart conditions so a
		// condition-disabled subchart's templates are NOT collected. Helm prunes disabled
		// subcharts from the tree before loading templates; jhelm previously collected
		// them
		// anyway, so a stale same-named define from a disabled library chart could win
		// the
		// global define collision (e.g. dask/druid's disabled mysql common overriding
		// postgresql's common.affinities.pods.soft, dropping `namespaces`).
		Map<String, Object> chartDefaults = (chart.getValues() != null) ? chart.getValues() : new HashMap<>();
		Map<String, Object> merged = mergeValues(chartDefaults, inheritedValues);
		List<Dependency> depMeta = (chart.getMetadata() != null) ? chart.getMetadata().getDependencies() : null;
		for (Chart subchart : chart.getDependencies()) {
			String subchartName = (subchart.getAlias() != null) ? subchart.getAlias()
					: subchart.getMetadata().getName();
			Dependency depEntry = findDependencyMetadata(depMeta, subchart);
			if (depEntry != null && depEntry.getCondition() != null && !depEntry.getCondition().isEmpty()
					&& !evaluateDependencyCondition(depEntry.getCondition(), merged)) {
				continue;
			}
			Object sliced = merged.get(subchartName);
			@SuppressWarnings("unchecked")
			Map<String, Object> subchartValues = (sliced instanceof Map) ? (Map<String, Object>) sliced
					: new HashMap<>();
			collectAllTemplates(subchart, templates, templateToChartName, templateFullPath, rootChartName,
					pathPrefix + "/charts/" + subchart.getMetadata().getName(), subchartValues);
		}
	}

	/**
	 * Registers a template file that collides on its {@code chartName/templates/file} key
	 * with a different file (distinct content), under a version-qualified key so the
	 * second pass still parses it and registers its named templates. The value is only
	 * ever a helper included by {@code define} name, so the synthetic key just needs to
	 * be unique — it is never referenced by path. Its real full path is recorded so it is
	 * still ordered like Helm during parsing.
	 */
	private static void preserveDistinctTemplate(Map<String, String> templates, Map<String, String> templateToChartName,
			Map<String, String> templateFullPath, String chartName, String fullPath, String version, String fileName,
			String content) {
		String key = chartName + "@" + ((version != null) ? version : "") + "/templates/" + fileName;
		if (!templates.containsKey(key)) {
			templates.put(key, content);
			templateToChartName.put(key, chartName);
			if (fullPath != null) {
				templateFullPath.put(key, fullPath);
			}
		}
	}

	/**
	 * Compare two version strings by splitting on "." and comparing each segment
	 * numerically. Returns positive if v1 &gt; v2, negative if v1 &lt; v2, zero if equal.
	 */
	static int compareVersions(String v1, String v2) {
		if (v1 == null && v2 == null) {
			return 0;
		}
		if (v1 == null) {
			return -1;
		}
		if (v2 == null) {
			return 1;
		}
		String[] parts1 = v1.split("\\.");
		String[] parts2 = v2.split("\\.");
		int len = Math.max(parts1.length, parts2.length);
		for (int i = 0; i < len; i++) {
			int n1 = (i < parts1.length) ? parseSegment(parts1[i]) : 0;
			int n2 = (i < parts2.length) ? parseSegment(parts2[i]) : 0;
			if (n1 != n2) {
				return Integer.compare(n1, n2);
			}
		}
		return 0;
	}

	private static int parseSegment(String segment) {
		try {
			return Integer.parseInt(segment);
		}
		catch (NumberFormatException ex) {
			// Strip non-numeric suffix (e.g. "1-beta") and parse the leading digits
			StringBuilder digits = new StringBuilder();
			for (char ch : segment.toCharArray()) {
				if (Character.isDigit(ch)) {
					digits.append(ch);
				}
				else {
					break;
				}
			}
			if (digits.length() > 0) {
				return Integer.parseInt(digits.toString());
			}
			return 0;
		}
	}

	private String renderWithSubcharts(Chart chart, Map<String, Object> values, Map<String, Object> releaseInfo,
			Set<String> renderedCharts, int depth, Capabilities capabilities) {
		String chartKey = chart.getMetadata().getName() + ":" + chart.getMetadata().getVersion();
		if (renderedCharts.contains(chartKey)) {
			if (log.isDebugEnabled()) {
				log.debug("Chart {} already rendered in this path, skipping to avoid recursion", chartKey);
			}
			return "";
		}
		if (depth > 3) { // Even further reduced depth
			if (log.isWarnEnabled()) {
				log.warn("Subchart nesting depth too high (>3) for chart {}", chartKey);
			}
			return "";
		}
		renderedCharts.add(chartKey);

		// Process import-values to enrich chart defaults from subchart values
		Map<String, Object> chartValues = (chart.getValues() != null) ? new HashMap<>(chart.getValues())
				: new HashMap<>();
		processImportValues(chart, chartValues);

		// Merge chart default values with provided values
		Map<String, Object> mergedValues = mergeValues(chartValues, values);

		// Helm makes subchart defaults available to parent templates via
		// .Values.<subchartName>.*
		mergeSubchartDefaults(chart, mergedValues);

		// Normalise the merged values to match Helm's value model (deep copy —
		// mergeValues
		// shares nested map references with the cached chart values):
		// * Numbers become Double, because Helm loads values via JSON where every number
		// is a float64. Interpolated directly this changes large ints (>=1e6) to Go's
		// scientific notation (`1000000` -> `1e+06`); `int`/`len` results stay Long and
		// render plain.
		// * Null map entries are pruned for a subchart (depth > 0) but kept for the
		// top-level release chart, mirroring Helm's coalesce ("a null key removes it",
		// applied while coalescing subcharts).
		// Pruned view for THIS chart's own rendering: Helm removes null keys while
		// coalescing a subchart (depth > 0); the top-level release chart keeps them.
		Map<String, Object> renderValues = prepareRenderValues(mergedValues, chartValues, depth > 0);
		// Unpruned view (null tombstones retained) for slicing down to subcharts. A
		// parent's null override must survive to DELETE the subchart's same-named default
		// (Helm's coalesce nil-deletion). Pruning before slicing drops the tombstone and
		// lets the subchart re-introduce its default — e.g. signoz nulls
		// clickhouse.zookeeper.image.registry to drop bitnami zookeeper's docker.io. The
		// subchart prunes the null itself when it renders.
		Map<String, Object> subchartSliceValues = prepareRenderValues(mergedValues, null, false);

		// Validate merged values against the chart's JSON Schema (if present)
		if (chart.getValuesSchema() != null) {
			try {
				schemaValidator.validate(chart.getMetadata().getName(), chart.getValuesSchema(), renderValues);
			}
			catch (SchemaValidationException ex) {
				throw new TemplateRenderException("Values schema validation failed: " + ex.getMessage(), ex,
						chart.getMetadata().getName(), null);
			}
		}

		// Wrap as Helm's chartutil.Values so `.Values.AsMap` resolves (gotohelm charts
		// such as redpanda's console/operator read the raw map via {{ .Values.AsMap }}).
		mergedValues = new Values(renderValues);

		// Helm's .Chart.IsRoot: true only for the top-level release chart (depth 0).
		chart.getMetadata().setRoot(depth == 0);

		Map<String, Object> context = new HashMap<>();
		context.put("Values", mergedValues);
		context.put("Chart", chart.getMetadata());
		context.put("Release", releaseInfo);

		// Add standard Helm objects
		context.put("Capabilities", buildCapabilities(capabilities));
		String chartBasePath = chart.getMetadata().getName() + "/templates";
		context.put("Template", Map.of("Name", "", "BasePath", chartBasePath));
		context.put("Files", new ChartFiles(chart.getFiles()));

		// Build .Subcharts map: subchart name/alias → full context (Chart, Values,
		// Release)
		// In Helm, .Subcharts.<name> provides a complete rendering context so that
		// templates referenced via include can access .Values, .Release, etc.
		Map<String, Object> subcharts = new HashMap<>();
		for (Chart dep : chart.getDependencies()) {
			String depKey = (dep.getAlias() != null) ? dep.getAlias() : dep.getMetadata().getName();
			@SuppressWarnings("unchecked")
			Map<String, Object> subchartValues = (Map<String, Object>) mergedValues.getOrDefault(depKey,
					new HashMap<>());
			Map<String, Object> subchartContext = new HashMap<>();
			subchartContext.put("Chart", dep.getMetadata());
			subchartContext.put("Values", new Values(subchartValues));
			subchartContext.put("Release", releaseInfo);
			subcharts.put(depKey, subchartContext);
		}
		context.put("Subcharts", subcharts);

		StringBuilder sb = new StringBuilder();

		// Render subcharts first, then this chart's own templates, so that when an
		// umbrella
		// and a subchart emit a resource with the SAME name (e.g. grafana/oncall: the
		// grafana subchart's fullname collapses to the release name because it contains
		// "grafana", so both create ServiceAccount/release-grafana-oncall) the PARENT's
		// resource is the one that survives a kind+name dedup — matching Helm, whose
		// manifest orders charts/<sub>/... before templates/... within each kind.
		// Subchart
		// values are sliced from subchartSliceValues (computed before any rendering), so
		// order does not affect them.
		List<Dependency> parentDepMeta = (chart.getMetadata() != null) ? chart.getMetadata().getDependencies() : null;
		for (Chart subchart : chart.getDependencies()) {
			// Use alias as lookup key when set (alias takes precedence over chart name)
			String subchartName = (subchart.getAlias() != null) ? subchart.getAlias()
					: subchart.getMetadata().getName();

			// Evaluate dependency condition from Chart.yaml/requirements.yaml (e.g.
			// "datadog.operator.enabled"). Condition paths are checked against the
			// PARENT chart's merged values. If the condition evaluates to false the
			// subchart is skipped.
			Dependency depMeta = findDependencyMetadata(parentDepMeta, subchart);
			if (depMeta != null && depMeta.getCondition() != null && !depMeta.getCondition().isEmpty()) {
				if (!evaluateDependencyCondition(depMeta.getCondition(), mergedValues)) {
					if (log.isDebugEnabled()) {
						log.debug("Subchart {} disabled by condition '{}'", subchartName, depMeta.getCondition());
					}
					continue;
				}
			}

			// Subcharts are only rendered if enabled in Values. Slice from the unpruned
			// view so a parent's null override reaches the subchart and deletes its
			// default.
			@SuppressWarnings("unchecked")
			Map<String, Object> subchartOverrides = (Map<String, Object>) subchartSliceValues.getOrDefault(subchartName,
					new HashMap<>());

			if (log.isDebugEnabled()) {
				log.debug("Subchart {}: overrides={}, enabled={}", subchartName, subchartOverrides,
						subchartOverrides.get("enabled"));
			}

			// Do NOT skip on a bare `enabled: false` in the merged values. Helm only
			// disables a subchart through an explicit Chart.yaml `condition` (or tags),
			// evaluated above — a standalone `<subchart>.enabled` is just an ordinary
			// value unless a condition references it. Some subcharts ship their own
			// top-level `enabled` default (e.g. jupyterhub, pulled in by dask/daskhub
			// with no condition); Helm renders them, so jhelm must too.
			// mergeSubchartDefaults
			// folds that default into the slice, which previously tripped a non-Helm
			// skip.

			// In Helm, global values are deep-merged: parent globals override subchart
			// globals
			if (mergedValues.containsKey("global")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> parentGlobal = (Map<String, Object>) mergedValues.get("global");
				@SuppressWarnings("unchecked")
				Map<String, Object> subGlobal = (Map<String, Object>) subchartOverrides.getOrDefault("global",
						new HashMap<>());
				subchartOverrides.put("global", mergeValues(subGlobal, parentGlobal));
			}

			if (log.isDebugEnabled()) {
				log.debug("Rendering subchart: {}", subchartName);
			}
			sb.append(renderWithSubcharts(subchart, subchartOverrides, releaseInfo, renderedCharts, depth + 1,
					capabilities));
		}

		// This chart's own templates render last (see the ordering note above).
		renderChartTemplates(chart, context, sb);

		return sb.toString();
	}

	private void renderChartTemplates(Chart chart, Map<String, Object> context, StringBuilder sb) {
		// Library charts only provide named templates via include — do not render
		// their .yaml files as standalone resources
		if ("library".equals(chart.getMetadata().getType())) {
			return;
		}
		// Helm 4 executes templates in reverse alphabetical order so that
		// zzz_*.yaml setup templates (e.g. istiod's zzz_profile.yaml which
		// merges _internal_defaults into .Values) run before other templates.
		List<Chart.Template> sorted = new ArrayList<>(chart.getTemplates());
		sorted.sort(Comparator.comparing(Chart.Template::getName, Comparator.reverseOrder()));
		for (Chart.Template t : sorted) {
			// Helm renders every file under templates/ as a manifest source EXCEPT
			// partials (basename starts with `_`) and NOTES.txt — the extension is
			// irrelevant, so a non-underscore `.tpl` is rendered too (e.g.
			// projectcapsule/capsule's crds.tpl, which emits the CRD ConfigMaps).
			String name = t.getName();
			int slash = name.lastIndexOf('/');
			String base = (slash >= 0) ? name.substring(slash + 1) : name;
			if (base.startsWith("_") || "NOTES.txt".equals(base)) {
				continue;
			}
			try {
				String helmStyleName = chart.getMetadata().getName() + "/templates/" + t.getName();
				parseWithCache(helmStyleName, t.getData());
				StringWriter writer = new StringWriter();

				// Share context across all template executions within a chart so that
				// cross-template mutations via set $ "Values" propagate (Go Helm
				// behavior)
				@SuppressWarnings("unchecked")
				Map<String, Object> templateMap = new HashMap<>((Map<String, Object>) context.get("Template"));
				templateMap.put("Name", helmStyleName);
				context.put("Template", templateMap);

				factory.execute(helmStyleName, context, writer);
				String rendered = writer.toString();
				if (rendered != null && !rendered.isBlank()) {
					// Emit a Helm-style source marker so the manifest records which
					// template
					// produced each document (enables `--show-only` / `--output-dir` and
					// matches `helm template` output).
					sb.append("# Source: ").append(helmStyleName).append('\n');
					if (!rendered.trim().endsWith("---")) {
						sb.append(rendered);
						if (!rendered.endsWith("\n")) {
							sb.append('\n');
						}
						sb.append("---\n");
					}
					else {
						sb.append(rendered);
					}
				}
			}
			catch (StackOverflowError ex) {
				// Fail loudly instead of silently dropping the template from the
				// manifest.
				String chartName = chart.getMetadata().getName();
				throw new TemplateRenderException("recursive template inclusion or too deep nesting while rendering "
						+ "template '" + t.getName() + "' of chart '" + chartName + "'", ex, chartName, t.getName());
			}
			catch (Exception ex) {
				String chartName = chart.getMetadata().getName();
				if (log.isDebugEnabled()) {
					log.debug("Failed to render chart '{}', template '{}': {}", chartName, t.getName(),
							ex.getMessage());
				}
				throw new TemplateRenderException("Rendering failed: " + ex.getMessage(), ex, chartName, t.getName());
			}
		}
	}

	/**
	 * Merge each subchart's default values under its name/alias key so parent templates
	 * can access them via {@code .Values.<subchartName>.*}.
	 */
	@SuppressWarnings("unchecked")
	private void mergeSubchartDefaults(Chart chart, Map<String, Object> mergedValues) {
		// Helm copies the entire top-level .Values.global into every subchart's tree
		// (.Values.<sub>.global), recursively, with the parent global winning. Thread it
		// through the coalesce so the parent-visible .Values matches Helm's (#21).
		Map<String, Object> topGlobal = (mergedValues.get("global") instanceof Map)
				? (Map<String, Object>) mergedValues.get("global") : Map.of();
		List<Dependency> depMetaList = (chart.getMetadata() != null) ? chart.getMetadata().getDependencies() : null;
		for (Chart dep : chart.getDependencies()) {
			// Helm's ProcessDependencies prunes a disabled subchart's defaults from
			// .Values — only the parent's own stub for it remains. Skip coalescing the
			// defaults of a subchart whose condition is false (#21).
			if (!isSubchartEnabledForCoalesce(depMetaList, dep, mergedValues)) {
				continue;
			}
			String depKey = (dep.getAlias() != null) ? dep.getAlias() : dep.getMetadata().getName();
			// Coalesce the subchart's full default tree — its own values.yaml plus its
			// nested subcharts' defaults, recursively — so parent templates that read
			// .Values.<sub>.<nestedsub>.* (e.g. gitlab's .Values.gitlab.webservice) see
			// the
			// same tree Helm builds. Anything already set on the parent for this key
			// wins. The parent's overrides for this subchart are threaded down so nested
			// dependency conditions see them (e.g. the umbrella disabling
			// prometheus.kube-state-metrics.enabled).
			Map<String, Object> existing = (Map<String, Object>) mergedValues.getOrDefault(depKey, new HashMap<>());
			Map<String, Object> depDefaults = coalesceChartValues(dep, topGlobal, existing);
			if (!depDefaults.isEmpty()) {
				mergedValues.put(depKey, mergeValues(depDefaults, existing));
			}
		}
	}

	/**
	 * Whether a subchart's defaults should be coalesced into the parent's
	 * {@code .Values}. Mirrors Helm: a subchart whose {@code condition} (from the
	 * parent's {@code Chart.yaml}) evaluates false is disabled, and Helm does not merge
	 * its {@code values.yaml} defaults into {@code .Values} (only the parent's own stub
	 * remains). A subchart with no condition is always coalesced.
	 * @param parentDepMeta the parent chart's dependency declarations
	 * @param dep the subchart being considered
	 * @param values the values to evaluate the condition against
	 * @return {@code true} if the subchart's defaults should be coalesced
	 */
	private boolean isSubchartEnabledForCoalesce(List<Dependency> parentDepMeta, Chart dep,
			Map<String, Object> values) {
		Dependency meta = findDependencyMetadata(parentDepMeta, dep);
		if (meta != null && meta.getCondition() != null && !meta.getCondition().isEmpty()) {
			return evaluateDependencyCondition(meta.getCondition(), values);
		}
		return true;
	}

	/**
	 * Recursively coalesce a chart's default values: its own {@code values.yaml} with
	 * each subchart's coalesced defaults placed under the subchart's name/alias key. The
	 * chart's own values for a subchart key win over that subchart's defaults (Helm
	 * precedence). The top-level {@code global} is deep-merged into this chart's tree
	 * (the chart's own global defaults are the base, {@code topGlobal} wins), matching
	 * Helm's global propagation into every subchart.
	 * @param chart the chart whose default tree to coalesce
	 * @param topGlobal the umbrella chart's coalesced {@code global} values, propagated
	 * into this subchart's {@code global} key
	 * @param overrides the parent's values for this chart, merged in only to evaluate
	 * nested dependency conditions (so an umbrella disabling
	 * {@code <sub>.<nested>.enabled} prunes the nested subchart's defaults, as Helm
	 * does); they are not folded into the returned defaults (the caller applies them with
	 * precedence)
	 * @return the coalesced default value tree
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> coalesceChartValues(Chart chart, Map<String, Object> topGlobal,
			Map<String, Object> overrides) {
		Map<String, Object> base = (chart.getValues() != null) ? new HashMap<>(chart.getValues()) : new HashMap<>();
		if (topGlobal != null && !topGlobal.isEmpty()) {
			Map<String, Object> ownGlobal = (base.get("global") instanceof Map)
					? (Map<String, Object>) base.get("global") : new HashMap<>();
			base.put("global", mergeValues(ownGlobal, topGlobal));
		}
		// Effective values for condition checks: the chart's own values with the parent's
		// overrides applied (overrides win), matching the values Helm evaluates
		// conditions
		// against.
		Map<String, Object> effective = (overrides != null && !overrides.isEmpty()) ? mergeValues(base, overrides)
				: base;
		List<Dependency> depMetaList = (chart.getMetadata() != null) ? chart.getMetadata().getDependencies() : null;
		for (Chart dep : chart.getDependencies()) {
			if (!isSubchartEnabledForCoalesce(depMetaList, dep, effective)) {
				continue;
			}
			String depKey = (dep.getAlias() != null) ? dep.getAlias() : dep.getMetadata().getName();
			Map<String, Object> depOverrides = (overrides != null && overrides.get(depKey) instanceof Map)
					? (Map<String, Object>) overrides.get(depKey) : Map.of();
			Map<String, Object> depDefaults = coalesceChartValues(dep, topGlobal, depOverrides);
			if (depDefaults.isEmpty()) {
				continue;
			}
			Object existing = base.get(depKey);
			Map<String, Object> baseOverrides = (existing instanceof Map) ? (Map<String, Object>) existing
					: new HashMap<>();
			base.put(depKey, mergeValues(depDefaults, baseOverrides));
		}
		return base;
	}

	/**
	 * Process {@code import-values} directives from chart dependency metadata. For each
	 * dependency that declares {@code import-values}, values are extracted from the
	 * subchart's defaults and placed into the parent chart's defaults.
	 *
	 * <p>
	 * Two forms are supported:
	 * <ul>
	 * <li>String: imports from subchart's {@code exports.<value>} into parent root</li>
	 * <li>Map with {@code child}/{@code parent}: imports from subchart path to parent
	 * path</li>
	 * </ul>
	 */
	@SuppressWarnings("unchecked")
	private void processImportValues(Chart chart, Map<String, Object> chartValues) {
		List<Dependency> depMetadata = (chart.getMetadata() != null) ? chart.getMetadata().getDependencies() : null;
		if (depMetadata == null || depMetadata.isEmpty()) {
			return;
		}
		for (Dependency dep : depMetadata) {
			if (dep.getImportValues() == null || dep.getImportValues().isEmpty()) {
				continue;
			}
			String depKey = (dep.getAlias() != null && !dep.getAlias().isEmpty()) ? dep.getAlias() : dep.getName();
			Chart subchart = findSubchart(chart, depKey);
			if (subchart == null) {
				continue;
			}
			Map<String, Object> subValues = (subchart.getValues() != null) ? subchart.getValues() : Map.of();
			for (Object iv : dep.getImportValues()) {
				if (iv instanceof Map<?, ?> m) {
					String child = String.valueOf(m.get("child"));
					String parent = String.valueOf(m.get("parent"));
					Object value = getNestedValue(subValues, child);
					if (value != null) {
						setNestedValue(chartValues, parent, value);
					}
				}
				else if (iv instanceof String s) {
					Object value = getNestedValue(subValues, "exports." + s);
					if (value instanceof Map<?, ?> exportedMap) {
						for (Map.Entry<?, ?> entry : exportedMap.entrySet()) {
							chartValues.putIfAbsent(String.valueOf(entry.getKey()), entry.getValue());
						}
					}
				}
			}
		}
	}

	private Chart findSubchart(Chart chart, String key) {
		for (Chart dep : chart.getDependencies()) {
			String name = (dep.getAlias() != null) ? dep.getAlias() : dep.getMetadata().getName();
			if (key.equals(name)) {
				return dep;
			}
		}
		return null;
	}

	private Object getNestedValue(Map<String, Object> map, String path) {
		String[] parts = path.split("\\.");
		Object current = map;
		for (String part : parts) {
			if (!(current instanceof Map<?, ?>)) {
				return null;
			}
			current = ((Map<?, ?>) current).get(part);
			if (current == null) {
				return null;
			}
		}
		return current;
	}

	@SuppressWarnings("unchecked")
	private void setNestedValue(Map<String, Object> map, String path, Object value) {
		String[] parts = path.split("\\.");
		Map<String, Object> current = map;
		for (int i = 0; i < parts.length - 1; i++) {
			Object next = current.get(parts[i]);
			if (!(next instanceof Map<?, ?>)) {
				Map<String, Object> newMap = new HashMap<>();
				current.put(parts[i], newMap);
				current = newMap;
			}
			else {
				current = (Map<String, Object>) next;
			}
		}
		String lastKey = parts[parts.length - 1];
		current.putIfAbsent(lastKey, value);
	}

	/**
	 * Apply aliases from dependency metadata to subchart Chart objects. In Helm, when a
	 * dependency declares {@code alias: operator}, the subchart's {@code .Chart.Name}
	 * becomes the alias. This must be applied before template collection so that template
	 * keys and rendering context use the alias consistently.
	 */
	private void applyAliasesFromMetadata(Chart chart) {
		List<Dependency> depMeta = (chart.getMetadata() != null) ? chart.getMetadata().getDependencies() : null;
		if (depMeta == null) {
			return;
		}
		for (Chart subchart : chart.getDependencies()) {
			Dependency dep = findDependencyMetadata(depMeta, subchart);
			if (dep != null && dep.getAlias() != null && !dep.getAlias().isEmpty()) {
				subchart.setAlias(dep.getAlias());
				subchart.getMetadata().setName(dep.getAlias());
			}
			// Recurse into nested subcharts
			applyAliasesFromMetadata(subchart);
		}
	}

	/**
	 * Find the {@link Dependency} metadata entry that corresponds to the given subchart.
	 */
	private Dependency findDependencyMetadata(List<Dependency> deps, Chart subchart) {
		if (deps == null) {
			return null;
		}
		// An exact alias match wins over any name match — when one subchart is aliased
		// several times (e.g. grafana-loki's memcached -> memcachedfrontend/chunks/…),
		// the
		// alias is the only thing that tells the instances apart, so it must be checked
		// across ALL dependencies before falling back to the chart name.
		String chartAlias = subchart.getAlias();
		if (chartAlias != null) {
			for (Dependency dep : deps) {
				if (chartAlias.equals(dep.getAlias())) {
					return dep;
				}
			}
		}
		String chartName = subchart.getMetadata().getName();
		for (Dependency dep : deps) {
			if (dep.getName().equals(chartName)) {
				return dep;
			}
		}
		return null;
	}

	/**
	 * Evaluate a dependency condition expression against chart values. Condition is a
	 * comma-separated list of dot-separated value paths. The first path that resolves to
	 * a boolean value wins. If no path resolves, the dependency is included by default.
	 */
	private boolean evaluateDependencyCondition(String condition, Map<String, Object> values) {
		String[] paths = condition.split(",");
		for (String path : paths) {
			path = path.trim();
			String[] parts = path.split("\\.");
			Object current = values;
			boolean found = true;
			for (String part : parts) {
				if (!(current instanceof Map<?, ?>)) {
					found = false;
					break;
				}
				current = ((Map<?, ?>) current).get(part);
				if (current == null) {
					found = false;
					break;
				}
			}
			if (found) {
				if (current instanceof Boolean b) {
					return b;
				}
				if (current instanceof String s) {
					return Boolean.parseBoolean(s);
				}
			}
		}
		// No condition path resolved — include by default
		return true;
	}

	/**
	 * Returns a deep copy of the merged values normalised to Helm's value model: every
	 * integer-typed number becomes a {@code Double} (Helm loads values via JSON, where
	 * all numbers are {@code float64}), and — when {@code pruneNulls} is set (subchart
	 * render) — null-valued map entries are dropped (Helm's "a null key removes it"
	 * coalesce rule; the top-level chart keeps its nulls). Null elements inside lists are
	 * preserved, as Helm only prunes table keys. A copy is made because
	 * {@link #mergeValues} shares nested map references with cached chart values, which
	 * must not be mutated.
	 * @param values the merged values map
	 * @param pruneNulls whether to drop null map entries (true for subcharts)
	 * @return a normalised deep copy
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> prepareRenderValues(Map<String, Object> values, Map<String, Object> chartDefaults,
			boolean pruneNulls) {
		return (Map<String, Object>) prepareValueNode(values, chartDefaults, pruneNulls);
	}

	@SuppressWarnings("unchecked")
	private Object prepareValueNode(Object node, Object defaultNode, boolean pruneNulls) {
		if (node instanceof Map) {
			Map<String, Object> src = (Map<String, Object>) node;
			Map<String, Object> defMap = (defaultNode instanceof Map) ? (Map<String, Object>) defaultNode : null;
			Map<String, Object> out = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : src.entrySet()) {
				Object value = entry.getValue();
				// Helm's coalesce deletes a null only when the chart's own defaults
				// declare
				// that key (coalesceValues iterates c.Values, deleting matching null
				// overrides). A standalone null — a --values null for a key the
				// (sub)chart
				// never declared — is never visited and is kept (#491). The blanket prune
				// here previously dropped those too.
				boolean deletesDefault = defMap != null && defMap.containsKey(entry.getKey());
				if (pruneNulls && value == null && deletesDefault) {
					continue;
				}
				Object dv = (defMap != null) ? defMap.get(entry.getKey()) : null;
				out.put(entry.getKey(), prepareValueNode(value, dv, pruneNulls));
			}
			return out;
		}
		if (node instanceof List) {
			List<Object> src = (List<Object>) node;
			List<Object> out = new ArrayList<>(src.size());
			for (Object item : src) {
				out.add(prepareValueNode(item, null, pruneNulls));
			}
			return out;
		}
		// Helm's values numbers are all float64; integer types become Double so a direct
		// interpolation matches Go's float formatting. Double/Float and BigDecimal are
		// left
		// as-is (already floating), and booleans/strings are untouched.
		if (node instanceof Integer || node instanceof Long || node instanceof Short || node instanceof Byte
				|| node instanceof BigInteger) {
			return ((Number) node).doubleValue();
		}
		return node;
	}

	private Map<String, Object> mergeValues(Map<String, Object> defaults, Map<String, Object> overrides) {
		if (defaults == null) {
			return new HashMap<>(overrides);
		}
		if (overrides == null) {
			return new HashMap<>(defaults);
		}

		Map<String, Object> merged = new HashMap<>(defaults);
		for (Map.Entry<String, Object> entry : overrides.entrySet()) {
			Object overrideValue = entry.getValue();
			Object defaultValue = merged.get(entry.getKey());

			if (overrideValue instanceof Map<?, ?> && defaultValue instanceof Map<?, ?>) {
				@SuppressWarnings("unchecked")
				Map<String, Object> defMap = (Map<String, Object>) defaultValue;
				@SuppressWarnings("unchecked")
				Map<String, Object> overMap = (Map<String, Object>) overrideValue;
				merged.put(entry.getKey(), mergeValues(defMap, overMap));
			}
			else {
				merged.put(entry.getKey(), overrideValue);
			}
		}
		return merged;
	}

}
