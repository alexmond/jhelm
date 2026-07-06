package org.alexmond.jhelm.core.service;

import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpEntity;

import java.io.BufferedInputStream;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Locale;
import java.util.Map;

import tools.jackson.databind.JsonNode;

import org.alexmond.jhelm.core.metrics.JhelmMetrics;
import org.alexmond.jhelm.core.model.RepositoryConfig;

@Slf4j
public class RepoManager {

	private final YAMLMapper yamlMapper;

	private final String configPath;

	private CloseableHttpClient httpClient;

	private RepoHttpClientFactory httpClientFactory;

	private OciRegistryClient ociClient;

	private final boolean insecureSkipTlsVerify;

	private final boolean blockPrivateNetworks;

	private final RegistryManager registryManager;

	// Optional metrics; set by the auto-configuration when a JhelmMetrics bean exists.
	// Null in library/direct-construction use (no instrumentation, no overhead).
	private JhelmMetrics metrics;

	// Optional repository-cache directory override (Helm's --repository-cache), set by
	// the
	// auto-configuration from jhelm.repository-cache-path. Null -> standard resolution.
	private String repositoryCacheOverride;

	// Parsed repo indexes (the full entries map of an index.yaml) keyed by repo name.
	// A repo's index can be tens of MB (e.g. bitnami), so this is bounded by an LRU:
	// resolving many charts across many repos (the parity suite, or a long-running
	// server) would otherwise retain every repo's parsed index at once and exhaust the
	// heap. Access-ordered + size-capped, wrapped synchronized because dependency
	// resolution hits it from several threads (a stale double-parse is harmless,
	// corruption is not). Evicted indexes are re-parsed from the on-disk cache file.
	private static final int INDEX_CACHE_MAX = 16;

	private final Map<String, Map<?, ?>> indexCache = Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Map<?, ?>> eldest) {
			return size() > INDEX_CACHE_MAX;
		}
	});

	/**
	 * Creates a manager for the default Helm repositories.yaml, no OCI auth, TLS
	 * verified.
	 */
	public RepoManager() {
		this(resolveDefaultConfigPath(), null, false);
	}

	/** Creates a manager for the given config path, no OCI auth, TLS verified. */
	public RepoManager(String configPath) {
		this(configPath, null, false);
	}

	/**
	 * Creates a manager for the default config path with the given OCI auth and TLS
	 * setting.
	 */
	public RepoManager(RegistryManager registryManager, boolean insecureSkipTlsVerify) {
		this(resolveDefaultConfigPath(), registryManager, insecureSkipTlsVerify);
	}

	/**
	 * Creates a manager for the default config path with the given OCI auth, TLS setting,
	 * and private-network policy.
	 * @param registryManager OCI registry auth provider (may be {@code null})
	 * @param insecureSkipTlsVerify whether to skip TLS verification on chart downloads
	 * @param blockPrivateNetworks whether outbound fetches also refuse private/site-local
	 * targets (server-mode strict SSRF policy)
	 */
	public RepoManager(RegistryManager registryManager, boolean insecureSkipTlsVerify, boolean blockPrivateNetworks) {
		this(resolveDefaultConfigPath(), registryManager, insecureSkipTlsVerify, blockPrivateNetworks);
	}

	/**
	 * Creates a repository manager.
	 * @param configPath path to the Helm repositories.yaml
	 * @param registryManager OCI registry auth provider (may be {@code null})
	 * @param insecureSkipTlsVerify whether to skip TLS verification on chart downloads
	 */
	public RepoManager(String configPath, RegistryManager registryManager, boolean insecureSkipTlsVerify) {
		this(configPath, registryManager, insecureSkipTlsVerify, false);
	}

	/**
	 * Creates a repository manager.
	 * @param configPath path to the Helm repositories.yaml
	 * @param registryManager OCI registry auth provider (may be {@code null})
	 * @param insecureSkipTlsVerify whether to skip TLS verification on chart downloads
	 * @param blockPrivateNetworks whether outbound fetches also refuse private/site-local
	 * targets (server-mode strict SSRF policy)
	 */
	public RepoManager(String configPath, RegistryManager registryManager, boolean insecureSkipTlsVerify,
			boolean blockPrivateNetworks) {
		LoadSettings loadSettings = LoadSettings.builder().setCodePointLimit(50_000_000).build();
		YAMLFactory yamlFactory = YAMLFactory.builder()
			.disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
			.loadSettings(loadSettings)
			.streamReadConstraints(StreamReadConstraints.builder().maxStringLength(50_000_000).build())
			.build();
		this.yamlMapper = YAMLMapper.builder(yamlFactory).build();
		this.configPath = configPath;
		this.registryManager = registryManager;
		this.insecureSkipTlsVerify = insecureSkipTlsVerify;
		this.blockPrivateNetworks = blockPrivateNetworks;
		initHttpClient();
	}

	/**
	 * Sets the optional metrics service used to instrument chart pulls. Wired by the
	 * auto-configuration; leave unset (null) to disable instrumentation.
	 * @param metrics the metrics service, or {@code null}
	 */
	public void setMetrics(JhelmMetrics metrics) {
		this.metrics = metrics;
	}

	private void recordChartPull(String source, JhelmMetrics.IoRunnable op) throws IOException {
		if (metrics == null) {
			op.run();
		}
		else {
			metrics.timeChartPull(source, op);
		}
	}

	void setHttpClientForTest(CloseableHttpClient client) {
		this.httpClient = client;
		this.httpClientFactory = new RepoHttpClientFactory(client, insecureSkipTlsVerify, blockPrivateNetworks);
		this.ociClient = new OciRegistryClient(client, blockPrivateNetworks);
	}

	/**
	 * Logs in to an OCI registry following Helm's flow: validate the credentials against
	 * the registry with a login handshake using the supplied transport options, then —
	 * only on success — store the credentials. The transport options ({@code --insecure},
	 * {@code --plain-http}, and the CA/cert/key files) apply to the handshake only and
	 * are not persisted, matching {@code helm registry login}.
	 * @param registry the registry hostname
	 * @param username the username
	 * @param password the password
	 * @param options the login-time transport options (never {@code null}; use
	 * {@link RegistryLoginOptions#none()})
	 * @throws IOException if the registry is unreachable, the TLS material is invalid,
	 * the credentials are rejected, or the credentials cannot be stored
	 */
	public void registryLogin(String registry, String username, String password, RegistryLoginOptions options)
			throws IOException {
		if (registryManager == null) {
			throw new IOException("no registry credential store is configured");
		}
		RegistryLoginOptions opts = (options != null) ? options : RegistryLoginOptions.none();
		String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
		if (hasLoginTls(opts)) {
			// Custom transport: a fresh, self-contained client honoring the login TLS
			// flags.
			try (CloseableHttpClient loginClient = httpClientFactory.buildLoginClient(opts.caFile(), opts.certFile(),
					opts.keyFile(), opts.insecureSkipTlsVerify())) {
				new OciRegistryClient(loginClient, blockPrivateNetworks).verifyLogin(registry, auth, opts.plainHttp());
			}
		}
		else {
			// No custom TLS: reuse the shared OCI client (default trust, SSRF-guarded).
			ociClient.verifyLogin(registry, auth, opts.plainHttp());
		}
		registryManager.login(registry, username, password);
	}

	private static boolean hasLoginTls(RegistryLoginOptions opts) {
		return opts.insecureSkipTlsVerify() || isSet(opts.caFile()) || isSet(opts.certFile()) || isSet(opts.keyFile());
	}

	private static boolean isSet(String value) {
		return value != null && !value.isBlank();
	}

	private static String resolveDefaultConfigPath() {
		return resolveConfigPath(System.getenv("HELM_REPOSITORY_CONFIG"), System.getProperty("user.home"),
				System.getProperty("os.name"));
	}

	/**
	 * Resolves the repositories.yaml path the way Helm does:
	 * {@code $HELM_REPOSITORY_CONFIG} when set, otherwise the per-OS Helm default.
	 * Package-private for testing.
	 * @param helmRepositoryConfig the value of {@code $HELM_REPOSITORY_CONFIG} (may be
	 * {@code null})
	 * @param home the user home directory
	 * @param osName the OS name
	 * @return the resolved repositories.yaml path
	 */
	static String resolveConfigPath(String helmRepositoryConfig, String home, String osName) {
		if (helmRepositoryConfig != null && !helmRepositoryConfig.isBlank()) {
			return helmRepositoryConfig;
		}
		String os = osName.toLowerCase(Locale.ROOT);
		if (os.contains("mac")) {
			return Paths.get(home, "Library/Preferences/helm/repositories.yaml").toString();
		}
		if (os.contains("win")) {
			return Paths.get(System.getenv("APPDATA"), "helm/repositories.yaml").toString();
		}
		return Paths.get(home, ".config/helm/repositories.yaml").toString();
	}

	private void initHttpClient() {
		// SSRF-validating DNS resolver: a host resolving to loopback/cloud-metadata is
		// refused at connect time and the connection is pinned to the validated addresses
		// (no rebinding window). The client owns and closes the connection manager.
		// Shared
		// with the OCI client below.
		httpClient = HttpClients.custom()
			.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
				.setDnsResolver(new SsrfGuardingDnsResolver(blockPrivateNetworks))
				.build())
			.build();
		httpClientFactory = new RepoHttpClientFactory(httpClient, insecureSkipTlsVerify, blockPrivateNetworks);
		ociClient = new OciRegistryClient(httpClient, blockPrivateNetworks);
	}

	/**
	 * The SSRF-guarded HTTP client factory (validates fetch URLs, host-gates credentials,
	 * applies TLS per the global {@code insecureSkipTlsVerify} /
	 * {@code blockPrivateNetworks} policy). Shared with {@code ConfigServerClient} so a
	 * config-server fetch rides the same audited path as repository index/chart
	 * downloads.
	 * @return the shared HTTP client factory
	 */
	RepoHttpClientFactory httpClientFactory() {
		return httpClientFactory;
	}

	RepositoryConfig.Repository getRepository(String name) throws IOException {
		RepositoryConfig config = loadConfig();
		return config.getRepositories().stream().filter((r) -> r.getName().equals(name)).findFirst().orElse(null);
	}

	public RepositoryConfig loadConfig() throws IOException {
		File file = new File(configPath);
		if (!file.exists()) {
			return RepositoryConfig.builder()
				.repositories(new ArrayList<>())
				.apiVersion("")
				.generated(OffsetDateTime.now().toString())
				.build();
		}
		return yamlMapper.readValue(file, RepositoryConfig.class);
	}

	public void saveConfig(RepositoryConfig config) throws IOException {
		File file = new File(configPath);
		file.getParentFile().mkdirs();
		yamlMapper.writeValue(file, config);
	}

	public void addRepo(String name, String url) throws IOException {
		addRepo(RepositoryConfig.Repository.builder().name(name).url(url).build(), true, true);
	}

	/**
	 * Registers a chart repository, persisting its auth/TLS settings and optionally
	 * refreshing its index, matching {@code helm repo add} semantics.
	 * @param repo the repository to add (name, URL, and any credentials/TLS settings)
	 * @param update {@code true} to fetch the repository index immediately (Helm's
	 * default; {@code --no-update} passes {@code false})
	 * @param forceUpdate {@code true} to replace an existing repository of the same name
	 * ({@code --force-update}); when {@code false}, adding a name that already exists
	 * fails
	 * @throws IOException if the name is already registered and {@code forceUpdate} is
	 * {@code false}, or the config cannot be written
	 */
	public void addRepo(RepositoryConfig.Repository repo, boolean update, boolean forceUpdate) throws IOException {
		validateRepoName(repo.getName());
		RepositoryConfig config = loadConfig();
		boolean exists = config.getRepositories().stream().anyMatch((r) -> r.getName().equals(repo.getName()));
		if (exists && !forceUpdate) {
			throw new IOException("repository name (" + repo.getName()
					+ ") already exists, please specify a different name or use --force-update to replace it");
		}
		config.getRepositories().removeIf((r) -> r.getName().equals(repo.getName()));
		config.getRepositories().add(repo);
		config.setGenerated(OffsetDateTime.now().toString());
		saveConfig(config);
		if (update) {
			try {
				updateRepo(repo.getName());
			}
			catch (IOException ex) {
				if (log.isWarnEnabled()) {
					log.warn("Failed to update repo '{}' immediately after add: {}", repo.getName(), ex.getMessage());
				}
			}
		}
	}

	public void removeRepo(String name) throws IOException {
		RepositoryConfig config = loadConfig();
		config.getRepositories().removeIf((r) -> r.getName().equals(name));
		config.setGenerated(OffsetDateTime.now().toString());
		saveConfig(config);
	}

	public String getRepoUrl(String name) throws IOException {
		RepositoryConfig config = loadConfig();
		return config.getRepositories()
			.stream()
			.filter((r) -> r.getName().equals(name))
			.map(RepositoryConfig.Repository::getUrl)
			.findFirst()
			.orElse(null);
	}

	/**
	 * Returns the effective {@code repositories.yaml} path this manager reads and writes.
	 * @return the repository config path
	 */
	public String getConfigPath() {
		return this.configPath;
	}

	/**
	 * Overrides the repository index cache directory, taking precedence over
	 * {@code $HELM_REPOSITORY_CACHE} and the per-OS default. Wired from
	 * {@code jhelm.repository-cache-path} (Helm's {@code --repository-cache}); leave
	 * unset to use the standard resolution.
	 * @param path the cache directory path, or {@code null}/blank to clear the override
	 */
	public void setRepositoryCacheOverride(String path) {
		this.repositoryCacheOverride = path;
	}

	/**
	 * Returns the effective repository index cache directory (the configured override,
	 * else {@code $HELM_REPOSITORY_CACHE} or the per-OS default), without creating it.
	 * @return the repository cache directory path
	 */
	public String getRepositoryCachePath() {
		return resolveCacheBaseDir().getPath();
	}

	private File getCacheDir() {
		File base = resolveCacheBaseDir();
		base.mkdirs();
		return base;
	}

	private File resolveCacheBaseDir() {
		if (repositoryCacheOverride != null && !repositoryCacheOverride.isBlank()) {
			return Paths.get(repositoryCacheOverride).toFile();
		}
		return resolveCacheDir(System.getenv("HELM_REPOSITORY_CACHE"), System.getProperty("user.home"),
				System.getProperty("os.name"));
	}

	/**
	 * Resolves the repository index cache directory: {@code $HELM_REPOSITORY_CACHE} when
	 * set (so it can be shared with Helm), otherwise a per-OS jhelm-native default.
	 * Package-private for testing.
	 * @param helmRepositoryCache the value of {@code $HELM_REPOSITORY_CACHE} (may be
	 * {@code null})
	 * @param home the user home directory
	 * @param osName the OS name
	 * @return the resolved cache directory
	 */
	static File resolveCacheDir(String helmRepositoryCache, String home, String osName) {
		if (helmRepositoryCache != null && !helmRepositoryCache.isBlank()) {
			return Paths.get(helmRepositoryCache).toFile();
		}
		String os = osName.toLowerCase(Locale.ROOT);
		if (os.contains("mac")) {
			return Paths.get(home, "Library/Caches/jhelm/repository").toFile();
		}
		if (os.contains("win")) {
			return Paths.get(System.getenv("LOCALAPPDATA"), "jhelm/repository").toFile();
		}
		return Paths.get(home, ".cache/jhelm/repository").toFile();
	}

	// A repository name is used directly as a filename component for its on-disk index
	// cache, and repo names are attacker-controllable (e.g. the REST add-repo endpoint).
	// Restrict them to a single safe path segment so a name like "../../etc/foo" can't
	// escape the cache directory when the index is written.
	private static final Pattern SAFE_REPO_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");

	private static void validateRepoName(String repoName) {
		if (repoName == null || repoName.isBlank() || ".".equals(repoName) || "..".equals(repoName)
				|| !SAFE_REPO_NAME.matcher(repoName).matches()) {
			throw new IllegalArgumentException("Invalid repository name '" + repoName
					+ "': must match [A-Za-z0-9._-]+ and contain no path separators");
		}
	}

	private File getIndexCacheFile(String repoName) {
		validateRepoName(repoName);
		return new File(getCacheDir(), repoName + "-index.yaml");
	}

	File getChartCacheDir() {
		File dir = new File(getCacheDir(), "charts");
		dir.mkdirs();
		return dir;
	}

	File getChartCacheFile(String digest) {
		String hex = digest.startsWith("sha256:") ? digest.substring(7) : digest;
		return new File(getChartCacheDir(), hex + ".tgz");
	}

	String computeFileSha256(File file) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) != -1) {
					md.update(buf, 0, n);
				}
			}
			return hexString(md.digest());
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IOException("SHA-256 not available", ex);
		}
	}

	String computeBytesSha256(byte[] bytes) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return hexString(md.digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IOException("SHA-256 not available", ex);
		}
	}

	private String hexString(byte[] hash) {
		StringBuilder sb = new StringBuilder();
		for (byte b : hash) {
			String hex = Integer.toHexString(0xff & b);
			if (hex.length() == 1) {
				sb.append('0');
			}
			sb.append(hex);
		}
		return sb.toString();
	}

	void verifyBlobDigest(File file, String expectedDigest) throws IOException {
		if (expectedDigest == null || !expectedDigest.startsWith("sha256:")) {
			return;
		}
		String expected = expectedDigest.substring(7);
		String actual = computeFileSha256(file);
		if (!expected.equals(actual)) {
			throw new IOException(
					"Digest mismatch for downloaded blob: expected sha256:" + expected + ", got sha256:" + actual);
		}
		if (log.isDebugEnabled()) {
			log.debug("Blob digest verified: sha256:{}", expected);
		}
	}

	public void updateRepo(String name) throws IOException {
		RepositoryConfig.Repository repo = getRepository(name);
		String repoUrl = (repo != null) ? repo.getUrl() : null;
		if (repoUrl == null) {
			throw new IOException("Repository not found: " + name);
		}
		String indexUrl = repoUrl.endsWith("/") ? repoUrl + "index.yaml" : repoUrl + "/index.yaml";
		if (log.isInfoEnabled()) {
			log.info("Updating repository '{}' from {}", name, indexUrl);
		}

		HttpGet httpGet = new HttpGet(indexUrl);
		httpGet.setHeader("User-Agent", "jhelm");

		httpClientFactory.executeGet(httpGet, repo, (response) -> {
			int statusCode = response.getCode();
			if (statusCode != 200) {
				throw new IOException("Failed to download index from " + indexUrl + ": " + statusCode + " "
						+ response.getReasonPhrase());
			}
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				try (InputStream in = entity.getContent();
						OutputStream out = new FileOutputStream(getIndexCacheFile(name))) {
					in.transferTo(out);
				}
			}
			return null;
		});
		this.indexCache.remove(name);
		if (log.isInfoEnabled()) {
			log.info("Repository '{}' index updated", name);
		}
	}

	public void updateAll() throws IOException {
		RepositoryConfig config = loadConfig();
		for (RepositoryConfig.Repository r : config.getRepositories()) {
			try {
				updateRepo(r.getName());
			}
			catch (IOException ex) {
				if (log.isWarnEnabled()) {
					log.warn("Failed to update repo {}: {}", r.getName(), ex.getMessage());
				}
			}
		}
	}

	public List<ChartVersion> listCharts(String repoName) throws IOException {
		Map<?, ?> entries = loadIndexEntries(repoName);
		List<ChartVersion> result = new ArrayList<>();
		for (Map.Entry<?, ?> entry : entries.entrySet()) {
			String chartName = String.valueOf(entry.getKey());
			if (entry.getValue() instanceof List<?> list && !list.isEmpty()) {
				Object first = list.getFirst();
				if (first instanceof Map<?, ?> m) {
					String version = asString(m.get("version"));
					String appVersion = asString(m.get("appVersion"));
					String description = asString(m.get("description"));
					result.add(new ChartVersion(repoName + "/" + chartName, version, appVersion, description));
				}
			}
		}
		result.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
		return result;
	}

	public List<ChartVersion> getChartVersions(String repoName, String chartName) throws IOException {
		Map<?, ?> entries = loadIndexEntries(repoName);
		List<ChartVersion> result = new ArrayList<>();
		Object chartListObj = entries.get(chartName);
		if (!(chartListObj instanceof List<?> list)) {
			return result;
		}
		for (Object o : list) {
			if (o instanceof Map<?, ?> m) {
				String version = asString(m.get("version"));
				String appVersion = asString(m.get("appVersion"));
				String description = asString(m.get("description"));
				result.add(new ChartVersion(repoName + "/" + chartName, version, appVersion, description));
			}
		}
		result.sort((a, b) -> safeCompareVersions(b.getChartVersion(), a.getChartVersion()));
		return result;
	}

	private Map<?, ?> loadIndexEntries(String repoName) throws IOException {
		Map<?, ?> cached = this.indexCache.get(repoName);
		if (cached == null) {
			cached = parseIndexEntries(repoName);
			this.indexCache.put(repoName, cached);
		}
		return cached;
	}

	private Map<?, ?> parseIndexEntries(String repoName) throws IOException {
		File indexFile = getIndexCacheFile(repoName);
		InputStream indexIn;
		if (indexFile.exists()) {
			indexIn = new FileInputStream(indexFile);
		}
		else {
			RepositoryConfig.Repository repo = getRepository(repoName);
			String repoUrl = (repo != null) ? repo.getUrl() : null;
			if (repoUrl == null) {
				throw new IOException("Repository name is required. Found: " + repoName);
			}
			String indexUrl = repoUrl.endsWith("/") ? repoUrl + "index.yaml" : repoUrl + "/index.yaml";
			if (log.isInfoEnabled()) {
				log.info("Downloading index from {} ...", indexUrl);
			}

			HttpGet httpGet = new HttpGet(indexUrl);
			httpGet.setHeader("User-Agent", "jhelm");

			byte[] indexData = httpClientFactory.executeGet(httpGet, repo, (response) -> {
				int statusCode = response.getCode();
				if (statusCode != 200) {
					throw new IOException("Failed to download index from " + indexUrl + ": " + statusCode);
				}
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					return entity.getContent().readAllBytes();
				}
				else {
					throw new IOException("Empty response from " + indexUrl);
				}
			});
			indexIn = new ByteArrayInputStream(indexData);
		}
		try (InputStream in = indexIn) {
			Map<?, ?> root = yamlMapper.readValue(in, Map.class);
			Object entriesObj = root.get("entries");
			if (entriesObj instanceof Map<?, ?> entries) {
				return entries;
			}
			return Map.of();
		}
	}

	/**
	 * Compares two chart versions by SemVer precedence, matching how Helm selects the
	 * "latest" version to fetch. A leading {@code v} is ignored ({@code v0.5.0} ==
	 * release {@code 0.5.0}), the {@code major.minor.patch} core is compared numerically,
	 * and a release with no pre-release ranks <em>above</em> an otherwise-equal
	 * pre-release (so {@code 5.3.0} beats {@code 5.3.0-rc1} and the stale
	 * {@code v0.5.0}).
	 */
	int safeCompareVersions(String v1, String v2) {
		if (v1 == null && v2 == null) {
			return 0;
		}
		if (v1 == null) {
			return -1;
		}
		if (v2 == null) {
			return 1;
		}
		v1 = stripLeadingV(v1);
		v2 = stripLeadingV(v2);
		String core1 = v1;
		String pre1 = "";
		int dash1 = v1.indexOf('-');
		if (dash1 >= 0) {
			core1 = v1.substring(0, dash1);
			pre1 = v1.substring(dash1 + 1);
		}
		String core2 = v2;
		String pre2 = "";
		int dash2 = v2.indexOf('-');
		if (dash2 >= 0) {
			core2 = v2.substring(0, dash2);
			pre2 = v2.substring(dash2 + 1);
		}
		int coreCmp = compareCoreVersions(core1, core2);
		if (coreCmp != 0) {
			return coreCmp;
		}
		// Same major.minor.patch: per SemVer a normal version outranks a pre-release.
		boolean hasPre1 = !pre1.isEmpty();
		boolean hasPre2 = !pre2.isEmpty();
		if (hasPre1 != hasPre2) {
			return hasPre1 ? -1 : 1;
		}
		return pre1.compareTo(pre2);
	}

	private int compareCoreVersions(String core1, String core2) {
		String[] p1 = core1.split("\\.");
		String[] p2 = core2.split("\\.");
		int n = Math.max(p1.length, p2.length);
		for (int i = 0; i < n; i++) {
			String a = (i < p1.length) ? p1[i] : "0";
			String b = (i < p2.length) ? p2[i] : "0";
			int ai = parseIntSafe(a);
			int bi = parseIntSafe(b);
			if (ai != Integer.MIN_VALUE && bi != Integer.MIN_VALUE) {
				if (ai != bi) {
					return Integer.compare(ai, bi);
				}
			}
			else {
				int c = a.compareTo(b);
				if (c != 0) {
					return c;
				}
			}
		}
		return 0;
	}

	private String stripLeadingV(String v) {
		return (!v.isEmpty() && (v.charAt(0) == 'v' || v.charAt(0) == 'V')) ? v.substring(1) : v;
	}

	private int parseIntSafe(String s) {
		try {
			return Integer.parseInt(s);
		}
		catch (Exception ex) {
			return Integer.MIN_VALUE;
		}
	}

	private String asString(Object o) {
		return (o != null) ? String.valueOf(o) : null;
	}

	/**
	 * Unified pull that handles both repo-based and OCI charts. Detects OCI URLs by their
	 * {@code oci://} prefix.
	 * @param chart either {@code repo/chartName}, {@code repo/chartName:version}, or
	 * {@code oci://...}
	 * @param version optional version (for repo charts; OCI version is in the URL tag)
	 * @param destDir destination directory
	 */
	public void pull(String chart, String version, String destDir) throws IOException {
		pull(chart, version, destDir, false);
	}

	/**
	 * Downloads a chart, optionally also fetching its {@code .prov} provenance file (Helm
	 * {@code pull --prov}/{@code --verify}). Provenance fetching is not supported for
	 * {@code oci://} references in this path.
	 * @param chart {@code repo/chartName}, {@code repo/chartName:version}, or
	 * {@code oci://...}
	 * @param version optional version (for repo charts)
	 * @param destDir destination directory
	 * @param withProv when {@code true}, also download the chart's {@code .prov} file
	 * @throws IOException if the chart (or requested provenance) cannot be downloaded
	 */
	public void pull(String chart, String version, String destDir, boolean withProv) throws IOException {
		if (chart.startsWith("oci://")) {
			String fileName = deriveOciFileName(chart);
			pullOci(chart, destDir, fileName);
		}
		else {
			String resolvedVersion = version;
			if (resolvedVersion == null && chart.contains(":")) {
				int colon = chart.lastIndexOf(':');
				resolvedVersion = chart.substring(colon + 1);
				chart = chart.substring(0, colon);
			}
			if (resolvedVersion == null || resolvedVersion.isBlank()) {
				throw new IOException("version is required for repository chart pulls");
			}
			pull(chart, null, resolvedVersion, destDir, withProv);
		}
	}

	public static String deriveOciFileName(String ociUrl) {
		String raw = ociUrl.substring(6);
		String[] parts = raw.split("/");
		String last = parts[parts.length - 1];
		String name = last.contains(":") ? last.substring(0, last.indexOf(':')) : last;
		String chartTag = last.contains(":") ? last.substring(last.indexOf(':') + 1) : "latest";
		return name + "-" + chartTag + ".tgz";
	}

	public void pull(String chartFullName, String repoName, String version, String destDir) throws IOException {
		pull(chartFullName, repoName, version, destDir, false);
	}

	/**
	 * Downloads a chart from a registered repository, optionally also fetching its
	 * {@code .prov} provenance file (Helm {@code pull --prov}/{@code --verify}).
	 * @param chartFullName the chart name, optionally prefixed with {@code repo/}
	 * @param repoName the repository name (or {@code null} to take it from the prefix)
	 * @param version the chart version
	 * @param destDir the destination directory
	 * @param withProv when {@code true}, also download the chart's {@code .prov} file
	 * @throws IOException if the chart (or requested provenance) cannot be downloaded
	 */
	public void pull(String chartFullName, String repoName, String version, String destDir, boolean withProv)
			throws IOException {
		String finalChartName = chartFullName;
		String finalRepoName = repoName;

		if (chartFullName.contains("/")) {
			int slashIndex = chartFullName.lastIndexOf('/');
			finalRepoName = chartFullName.substring(0, slashIndex);
			finalChartName = chartFullName.substring(slashIndex + 1);
		}

		if (finalRepoName == null || finalRepoName.isEmpty()) {
			throw new IOException("Repository name is required (either as argument or as prefix in chart name)");
		}

		RepositoryConfig config = loadConfig();
		String searchRepoName = finalRepoName;
		RepositoryConfig.Repository repo = config.getRepositories()
			.stream()
			.filter((r) -> r.getName().equals(searchRepoName))
			.findFirst()
			.orElseThrow(() -> new IOException("Repository not found: " + searchRepoName));

		String[] indexEntry = lookupChartInIndex(getIndexCacheFile(finalRepoName), finalChartName, version);
		String chartUrl = resolveChartUrl((indexEntry != null) ? indexEntry[0] : null, repo.getUrl(), finalChartName,
				version);
		String chartDigest = (indexEntry != null) ? indexEntry[1] : null;
		String tgzFileName = finalChartName + "-" + version + ".tgz";

		if (chartDigest != null) {
			File cached = getChartCacheFile(chartDigest);
			if (cached.exists()) {
				if (log.isInfoEnabled()) {
					log.info("Using cached chart (digest: {})", chartDigest);
				}
				File destFile = new File(destDir, tgzFileName);
				Files.copy(cached.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				untar(destFile, new File(destDir));
				if (withProv) {
					fetchProvenance(chartUrl, destDir, tgzFileName, repo);
				}
				return;
			}
		}

		pullFromUrl(chartUrl, destDir, tgzFileName, repo);
		if (withProv) {
			fetchProvenance(chartUrl, destDir, tgzFileName, repo);
		}

		File downloaded = new File(destDir, tgzFileName);
		if (downloaded.exists()) {
			String actualDigest = computeFileSha256(downloaded);
			File cacheTarget = getChartCacheFile(actualDigest);
			if (!cacheTarget.exists()) {
				Files.copy(downloaded.toPath(), cacheTarget.toPath());
			}
		}
	}

	// Downloads the chart's detached PGP provenance (.prov) next to the .tgz — the file
	// Helm's --verify checks and --prov keeps. Unlike pullFromUrl this is a raw fetch (a
	// .prov is not a gzip archive, so it must not be untarred).
	private void fetchProvenance(String chartUrl, String destDir, String tgzFileName, RepositoryConfig.Repository repo)
			throws IOException {
		String provUrl = chartUrl + ".prov";
		File provFile = new File(destDir, tgzFileName + ".prov");
		HttpGet httpGet = new HttpGet(provUrl);
		httpGet.setHeader("User-Agent", "jhelm");
		httpClientFactory.executeGet(httpGet, repo, (response) -> {
			if (response.getCode() != 200) {
				throw new IOException("Failed to download provenance from " + provUrl + ": " + response.getCode());
			}
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				try (InputStream in = entity.getContent(); FileOutputStream fos = new FileOutputStream(provFile)) {
					in.transferTo(fos);
				}
			}
			return null;
		});
	}

	/**
	 * Pulls a chart directly from a repository URL (Helm's {@code --repo}), fetching that
	 * repo's {@code index.yaml} with the supplied credentials and TLS settings instead of
	 * resolving a named repository from {@code repositories.yaml}. Backs
	 * {@code jhelm pull --repo URL CHART --version V}.
	 * @param repoUrl the repository base URL (its {@code index.yaml} is fetched)
	 * @param chartName the chart name to resolve in the index
	 * @param version the chart version (required)
	 * @param destDir the destination directory
	 * @param auth a repository descriptor carrying auth and TLS settings; its URL is set
	 * to {@code repoUrl} (may be {@code null} for an anonymous pull)
	 * @throws IOException if the index or chart cannot be downloaded
	 */
	// S5443: the temp file is created via NIO with owner-only permissions and holds only
	// a
	// downloaded public repository index.yaml (no secrets), so the shared temp directory
	// is
	// used safely here.
	@SuppressWarnings("java:S5443")
	public void pullFromRepoUrl(String repoUrl, String chartName, String version, String destDir,
			RepositoryConfig.Repository auth) throws IOException {
		pullFromRepoUrl(repoUrl, chartName, version, destDir, auth, false);
	}

	/**
	 * As
	 * {@link #pullFromRepoUrl(String, String, String, String, RepositoryConfig.Repository)},
	 * optionally also fetching the chart's {@code .prov} provenance file (Helm
	 * {@code pull --repo ... --prov}/{@code --verify}).
	 * @param repoUrl the repository base URL
	 * @param chartName the chart name to resolve in the index
	 * @param version the chart version (required)
	 * @param destDir the destination directory
	 * @param auth a repository descriptor carrying auth and TLS settings, or {@code null}
	 * @param withProv when {@code true}, also download the chart's {@code .prov} file
	 * @throws IOException if the index, chart, or requested provenance cannot be
	 * downloaded
	 */
	@SuppressWarnings("java:S5443")
	public void pullFromRepoUrl(String repoUrl, String chartName, String version, String destDir,
			RepositoryConfig.Repository auth, boolean withProv) throws IOException {
		if (version == null || version.isBlank()) {
			throw new IOException("version is required when pulling with --repo");
		}
		RepositoryConfig.Repository repo = (auth != null) ? auth : RepositoryConfig.Repository.builder().build();
		repo.setUrl(repoUrl);
		String indexUrl = repoUrl.endsWith("/") ? repoUrl + "index.yaml" : repoUrl + "/index.yaml";
		// NIO createTempFile creates the file atomically with owner-only permissions on
		// POSIX, unlike File.createTempFile which uses the umask default in the shared
		// temp directory.
		File indexTmp = Files.createTempFile("jhelm-index-", ".yaml").toFile();
		try {
			HttpGet httpGet = new HttpGet(indexUrl);
			httpGet.setHeader("User-Agent", "jhelm");
			httpClientFactory.executeGet(httpGet, repo, (response) -> {
				if (response.getCode() != 200) {
					throw new IOException("Failed to download index from " + indexUrl + ": " + response.getCode() + " "
							+ response.getReasonPhrase());
				}
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					try (InputStream in = entity.getContent(); OutputStream out = new FileOutputStream(indexTmp)) {
						in.transferTo(out);
					}
				}
				return null;
			});
			String[] indexEntry = lookupChartInIndex(indexTmp, chartName, version);
			String chartUrl = resolveChartUrl((indexEntry != null) ? indexEntry[0] : null, repoUrl, chartName, version);
			String tgzFileName = chartName + "-" + version + ".tgz";
			pullFromUrl(chartUrl, destDir, tgzFileName, repo);
			if (withProv) {
				fetchProvenance(chartUrl, destDir, tgzFileName, repo);
			}
		}
		finally {
			Files.deleteIfExists(indexTmp.toPath());
		}
	}

	String[] lookupChartInIndex(File indexFile, String chartName, String version) throws IOException {
		if (!indexFile.exists()) {
			return null;
		}
		try (InputStream in = new FileInputStream(indexFile)) {
			Map<?, ?> root = yamlMapper.readValue(in, Map.class);
			Map<?, ?> entries = (Map<?, ?>) root.get("entries");
			if (entries == null) {
				return null;
			}
			List<?> versions = (List<?>) entries.get(chartName);
			if (versions == null) {
				return null;
			}
			for (Object o : versions) {
				Map<?, ?> m = (Map<?, ?>) o;
				if (version.equals(asString(m.get("version")))) {
					List<?> urls = (List<?>) m.get("urls");
					if (urls != null && !urls.isEmpty()) {
						return new String[] { asString(urls.get(0)), asString(m.get("digest")) };
					}
				}
			}
		}
		return null;
	}

	String resolveChartUrl(String indexUrl, String repoUrl, String chartName, String version) {
		if (indexUrl == null) {
			return repoUrl + "/" + chartName + "-" + version + ".tgz";
		}
		if (!indexUrl.contains("://")) {
			String base = repoUrl.endsWith("/") ? repoUrl : repoUrl + "/";
			return base + indexUrl;
		}
		return indexUrl;
	}

	public void pullFromUrl(String chartUrl, String destDir, String fileName) throws IOException {
		pullFromUrl(chartUrl, destDir, fileName, null);
	}

	void pullFromUrl(String chartUrl, String destDir, String fileName, RepositoryConfig.Repository repo)
			throws IOException {
		if (chartUrl.startsWith("oci://")) {
			pullOci(chartUrl, destDir, fileName);
			return;
		}
		recordChartPull("http", () -> doHttpPull(chartUrl, destDir, fileName, repo));
	}

	private void doHttpPull(String chartUrl, String destDir, String fileName, RepositoryConfig.Repository repo)
			throws IOException {
		if (log.isInfoEnabled()) {
			log.info("Pulling chart from {} to {}", chartUrl, destDir);
		}

		File destFile = new File(destDir, fileName);
		destFile.getParentFile().mkdirs();

		HttpGet httpGet = new HttpGet(chartUrl);
		httpGet.setHeader("User-Agent", "jhelm");

		httpClientFactory.executeGet(httpGet, repo, (response) -> {
			int statusCode = response.getCode();
			if (statusCode != 200) {
				throw new IOException("Failed to download chart from " + chartUrl + ": " + statusCode);
			}
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				try (InputStream in = entity.getContent(); FileOutputStream fos = new FileOutputStream(destFile)) {
					in.transferTo(fos);
				}
			}
			return null;
		});
		if (log.isInfoEnabled()) {
			log.info("Chart pulled successfully to {}", destFile.getAbsolutePath());
		}

		untar(destFile, new File(destDir));
	}

	public void pullOci(String ociUrl, String destDir, String fileName) throws IOException {
		recordChartPull("oci", () -> doPullOci(ociUrl, destDir, fileName));
	}

	private void doPullOci(String ociUrl, String destDir, String fileName) throws IOException {
		if (log.isInfoEnabled()) {
			log.info("Pulling OCI chart from {} to {}", ociUrl, destDir);
		}
		String[] ociParts = ociClient.parseOciUrl(ociUrl);
		String registry = ociParts[0];
		String path = ociParts[1];
		String tag = ociParts[2];

		String auth = (registryManager != null) ? registryManager.getAuth(registry) : null;
		String token = ociClient.fetchToken(registry, path, auth, "pull");

		String manifestUrl = "https://" + registry + "/v2/" + path + "/manifests/" + tag;
		JsonNode manifest;
		try {
			manifest = ociClient.getManifest(manifestUrl, token, "application/vnd.oci.image.manifest.v1+json");
		}
		catch (IOException ex) {
			if (log.isWarnEnabled()) {
				log.warn("Failed to get OCI manifest with v1+json, trying without specific accept header: {}",
						ex.getMessage());
			}
			manifest = ociClient.getManifest(manifestUrl, token, null);
		}

		if (ociClient.isManifestIndex(manifest)) {
			if (log.isDebugEnabled()) {
				log.debug("OCI manifest is an index, resolving to specific manifest");
			}
			String resolvedDigest = ociClient.resolveDigestFromIndex(manifest);
			if (resolvedDigest == null) {
				throw new IOException("No suitable manifest found in OCI index for " + ociUrl);
			}
			String specificManifestUrl = "https://" + registry + "/v2/" + path + "/manifests/" + resolvedDigest;
			manifest = ociClient.getManifest(specificManifestUrl, token, "application/vnd.oci.image.manifest.v1+json");
		}

		String digest = findChartLayerDigest(manifest, ociUrl);

		File cached = getChartCacheFile(digest);
		if (cached.exists()) {
			if (log.isInfoEnabled()) {
				log.info("Using cached OCI chart (digest: {})", digest);
			}
			File destFile = new File(destDir, fileName);
			Files.copy(cached.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			untar(destFile, new File(destDir));
			return;
		}

		String blobUrl = "https://" + registry + "/v2/" + path + "/blobs/" + digest;
		File destFile = new File(destDir, fileName);
		ociClient.downloadBlob(blobUrl, token, destFile);

		if (destFile.exists()) {
			verifyBlobDigest(destFile, digest);
			File cacheTarget = getChartCacheFile(digest);
			if (!cacheTarget.exists()) {
				Files.copy(destFile.toPath(), cacheTarget.toPath());
			}
		}

		untar(destFile, new File(destDir));
	}

	public void pushOci(String chartTgzPath, String ociUrl) throws IOException {
		if (log.isInfoEnabled()) {
			log.info("Pushing chart {} to {}", chartTgzPath, ociUrl);
		}
		String[] ociParts = ociClient.parseOciUrl(ociUrl);
		String registry = ociParts[0];
		String path = ociParts[1];
		String tag = ociParts[2];

		String auth = (registryManager != null) ? registryManager.getAuth(registry) : null;
		String token = ociClient.fetchToken(registry, path, auth, "push,pull");

		File chartFile = new File(chartTgzPath);
		if (!chartFile.exists()) {
			throw new IOException("Chart file not found: " + chartTgzPath);
		}
		byte[] chartBytes = Files.readAllBytes(chartFile.toPath());
		String chartDigestHex = computeBytesSha256(chartBytes);
		String chartDigest = "sha256:" + chartDigestHex;

		byte[] configBytes = "{}".getBytes(StandardCharsets.UTF_8);
		String configDigestHex = computeBytesSha256(configBytes);
		String configDigest = "sha256:" + configDigestHex;

		if (!ociClient.blobExists(registry, path, token, chartDigest)) {
			ociClient.uploadBlob(registry, path, token, chartDigest, chartBytes);
		}
		if (!ociClient.blobExists(registry, path, token, configDigest)) {
			ociClient.uploadBlob(registry, path, token, configDigest, configBytes);
		}

		String manifestJson = """
				{
				  "schemaVersion": 2,
				  "mediaType": "application/vnd.oci.image.manifest.v1+json",
				  "config": {
				    "mediaType": "application/vnd.cncf.helm.config.v1+json",
				    "digest": "%s",
				    "size": %d
				  },
				  "layers": [{
				    "mediaType": "application/vnd.cncf.helm.chart.content.v1.tar+gzip",
				    "digest": "%s",
				    "size": %d
				  }]
				}""".formatted(configDigest, configBytes.length, chartDigest, chartBytes.length);

		ociClient.pushManifest(registry, path, tag, token, manifestJson.getBytes(StandardCharsets.UTF_8));
		if (log.isInfoEnabled()) {
			log.info("Chart pushed successfully to {}", ociUrl);
		}
	}

	private String findChartLayerDigest(JsonNode manifest, String ociUrl) throws IOException {
		if (manifest.has("layers")) {
			for (JsonNode layer : manifest.get("layers")) {
				String mediaType = layer.get("mediaType").asString();
				if ("application/vnd.cncf.helm.chart.content.v1.tar+gzip".equals(mediaType)
						|| "application/vnd.oci.image.layer.v1.tar+gzip".equals(mediaType)) {
					return layer.get("digest").asString();
				}
			}
		}
		throw new IOException("No chart layer found in OCI manifest for " + ociUrl);
	}

	public void close() {
		if (httpClient != null) {
			try {
				httpClient.close();
			}
			catch (IOException ex) {
				if (log.isErrorEnabled()) {
					log.error("Failed to close HTTP client", ex);
				}
			}
		}
	}

	/**
	 * Maximum total uncompressed bytes a chart archive may expand to (decompression-bomb
	 * guard).
	 */
	private static final long MAX_UNTAR_TOTAL_BYTES = 1L << 30; // 1 GiB

	/**
	 * Maximum number of entries a chart archive may contain (decompression-bomb guard).
	 */
	private static final int MAX_UNTAR_ENTRIES = 50_000;

	public void untar(File tgzFile, File destDir) throws IOException {
		if (log.isInfoEnabled()) {
			log.info("Untarring {} to {}", tgzFile.getAbsolutePath(), destDir.getAbsolutePath());
		}
		// Use the JDK's GZIPInputStream rather than Commons Compress here: many
		// charts in the wild (notably GitHub-hosted charts packaged by chart-releaser)
		// carry a non-spec gzip extra field — a base64 blob that isn't framed as proper
		// SI1/SI2/LEN subfields. The JDK decoder (like gzip(1) and Helm) leniently skips
		// XLEN bytes, while Commons Compress strictly validates subfields and rejects the
		// whole archive ("Extra subfield length exceeds remaining bytes in extra").
		try (InputStream fi = Files.newInputStream(tgzFile.toPath());
				InputStream bi = new BufferedInputStream(fi);
				InputStream gzi = new GZIPInputStream(bi);
				TarArchiveInputStream ti = new TarArchiveInputStream(gzi)) {

			TarArchiveEntry entry;
			long totalBytes = 0;
			int entryCount = 0;
			while ((entry = ti.getNextEntry()) != null) {
				if (++entryCount > MAX_UNTAR_ENTRIES) {
					throw new IOException("chart archive has too many entries (> " + MAX_UNTAR_ENTRIES
							+ "): possible decompression bomb");
				}
				if (!ti.canReadEntryData(entry)) {
					continue;
				}
				File f = new File(destDir, entry.getName());
				String canonicalDest = destDir.getCanonicalPath() + File.separator;
				if (!f.getCanonicalPath().startsWith(canonicalDest)
						&& !f.getCanonicalPath().equals(destDir.getCanonicalPath())) {
					throw new IOException("Path traversal detected in chart archive: " + entry.getName());
				}
				if (entry.isDirectory()) {
					if (!f.isDirectory() && !f.mkdirs()) {
						throw new IOException("failed to create directory " + f);
					}
				}
				else {
					File parent = f.getParentFile();
					if (!parent.isDirectory() && !parent.mkdirs()) {
						throw new IOException("failed to create directory " + parent);
					}
					totalBytes = copyEntryBounded(ti, f, totalBytes);
				}
			}
		}
	}

	/**
	 * Copies a single tar entry to {@code dest}, enforcing a cumulative uncompressed-size
	 * cap ({@link #MAX_UNTAR_TOTAL_BYTES}) so a maliciously small archive cannot expand
	 * to exhaust disk (decompression bomb).
	 * @param ti the tar stream positioned at the entry's data
	 * @param dest the destination file
	 * @param totalBytesSoFar bytes already written across previous entries
	 * @return the updated running total of bytes written
	 * @throws IOException if writing fails or the cumulative size cap is exceeded
	 */
	private long copyEntryBounded(TarArchiveInputStream ti, File dest, long totalBytesSoFar) throws IOException {
		long total = totalBytesSoFar;
		byte[] buffer = new byte[8192];
		try (OutputStream o = Files.newOutputStream(dest.toPath())) {
			int read;
			while ((read = ti.read(buffer)) != -1) {
				total += read;
				if (total > MAX_UNTAR_TOTAL_BYTES) {
					throw new IOException("chart archive expands beyond the maximum allowed size ("
							+ MAX_UNTAR_TOTAL_BYTES + " bytes): possible decompression bomb");
				}
				o.write(buffer, 0, read);
			}
		}
		return total;
	}

	@lombok.Data
	@lombok.AllArgsConstructor
	public static class ChartVersion {

		private String name; // repo/chart

		private String chartVersion; // version

		private String appVersion; // appVersion (may be null)

		private String description; // description (may be null)

	}

}
