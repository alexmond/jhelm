package org.alexmond.jhelm.core.service;

import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;

import java.io.BufferedInputStream;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.hc.core5.http.Header;

import org.alexmond.jhelm.core.model.RepositoryConfig;

@Slf4j
public class RepoManager {

	private final YAMLMapper yamlMapper;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final String configPath;

	private CloseableHttpClient httpClient;

	@Setter
	private boolean insecureSkipTlsVerify;

	@Setter
	private RegistryManager registryManager;

	public RepoManager() {
		this(resolveDefaultConfigPath());
	}

	public RepoManager(String configPath) {
		LoadSettings loadSettings = LoadSettings.builder().setCodePointLimit(50_000_000).build();
		YAMLFactory yamlFactory = YAMLFactory.builder()
			.disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
			.loadSettings(loadSettings)
			.streamReadConstraints(StreamReadConstraints.builder().maxStringLength(50_000_000).build())
			.build();
		this.yamlMapper = YAMLMapper.builder(yamlFactory).build();
		this.configPath = configPath;
		initHttpClient();
	}

	void setHttpClientForTest(CloseableHttpClient client) {
		this.httpClient = client;
	}

	private static String resolveDefaultConfigPath() {
		String home = System.getProperty("user.home");
		String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
		if (os.contains("mac")) {
			return Paths.get(home, "Library/Preferences/helm/repositories.yaml").toString();
		}
		if (os.contains("win")) {
			return Paths.get(System.getenv("APPDATA"), "helm/repositories.yaml").toString();
		}
		return Paths.get(home, ".config/helm/repositories.yaml").toString();
	}

	private void initHttpClient() {
		try {
			// if (insecureSkipTlsVerify) {
			// TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String
			// authType) -> true;
			// SSLContext sslContext = SSLContextBuilder.create()
			// .loadTrustMaterial(null, acceptingTrustStrategy)
			// .build();
			// TlsConfig tlsConfig = TlsConfig.custom()
			// .setSslContext(sslContext)
			// .build();
			// var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
			// .setDefaultTlsConfig(tlsConfig)
			// .build();
			// httpClient = HttpClients.custom()
			// .setConnectionManager(connectionManager)
			// .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
			// .build();
			// } else {
			httpClient = HttpClients.createDefault();
			// }
		}
		catch (Exception ex) {
			if (log.isErrorEnabled()) {
				log.error("Failed to initialize HTTP client", ex);
			}
			httpClient = HttpClients.createDefault();
		}
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
		RepositoryConfig config = loadConfig();
		config.getRepositories().removeIf((r) -> r.getName().equals(name));
		RepositoryConfig.Repository repo = RepositoryConfig.Repository.builder().name(name).url(url).build();
		config.getRepositories().add(repo);
		config.setGenerated(OffsetDateTime.now().toString());
		saveConfig(config);
		// Eagerly update index on add for convenience, like `helm repo add` often
		// followed by update
		try {
			updateRepo(name);
		}
		catch (IOException ex) {
			if (log.isWarnEnabled()) {
				log.warn("Failed to update repo '{}' immediately after add: {}", name, ex.getMessage());
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

	private File getCacheDir() {
		String home = System.getProperty("user.home");
		String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
		File base;
		if (os.contains("mac")) {
			base = Paths.get(home, "Library/Caches/jhelm/repository").toFile();
		}
		else if (os.contains("win")) {
			base = Paths.get(System.getenv("LOCALAPPDATA"), "jhelm/repository").toFile();
		}
		else {
			base = Paths.get(home, ".cache/jhelm/repository").toFile();
		}
		base.mkdirs();
		return base;
	}

	private File getIndexCacheFile(String repoName) {
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
			byte[] hash = md.digest();
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
		catch (NoSuchAlgorithmException ex) {
			throw new IOException("SHA-256 not available", ex);
		}
	}

	String computeBytesSha256(byte[] bytes) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(bytes);
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
		catch (NoSuchAlgorithmException ex) {
			throw new IOException("SHA-256 not available", ex);
		}
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

	boolean isManifestIndex(JsonNode manifest) {
		if (manifest.has("manifests") && !manifest.has("layers")) {
			return true;
		}
		if (manifest.has("mediaType")) {
			String mt = manifest.get("mediaType").asString();
			return mt.contains("index") || mt.contains("manifest.list");
		}
		return false;
	}

	String resolveDigestFromIndex(JsonNode index) {
		JsonNode manifests = index.get("manifests");
		if (manifests == null || !manifests.isArray() || manifests.isEmpty()) {
			return null;
		}
		// Prefer entries without a platform restriction (platform-agnostic charts)
		for (JsonNode m : manifests) {
			if (!m.has("platform")) {
				return m.get("digest").asString();
			}
		}
		// Prefer linux/amd64
		for (JsonNode m : manifests) {
			if (m.has("platform")) {
				JsonNode platform = m.get("platform");
				String os = platform.has("os") ? platform.get("os").asString() : "";
				String arch = platform.has("architecture") ? platform.get("architecture").asString() : "";
				if ("linux".equals(os) && "amd64".equals(arch)) {
					return m.get("digest").asString();
				}
			}
		}
		// Fallback: first entry
		return manifests.get(0).get("digest").asString();
	}

	public void updateRepo(String name) throws IOException {
		String repoUrl = getRepoUrl(name);
		if (repoUrl == null) {
			throw new IOException("Repository not found: " + name);
		}
		String indexUrl = repoUrl.endsWith("/") ? repoUrl + "index.yaml" : repoUrl + "/index.yaml";
		if (log.isInfoEnabled()) {
			log.info("Updating repository '{}' from {}", name, indexUrl);
		}

		HttpGet httpGet = new HttpGet(indexUrl);
		httpGet.setHeader("User-Agent", "jhelm");

		httpClient.execute(httpGet, (response) -> {
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

	public List<ChartVersion> getChartVersions(String repoName, String chartName) throws IOException {
		// Prefer cached index if exists, else fetch live
		File indexFile = getIndexCacheFile(repoName);
		InputStream indexIn;
		if (indexFile.exists()) {
			indexIn = new FileInputStream(indexFile);
		}
		else {
			String repoUrl = getRepoUrl(repoName);
			if (repoUrl == null) {
				// If repoName is null or empty, it might be an absolute URL pull or
				// something else,
				// but for getChartVersions we need a repo.
				throw new IOException("Repository name is required to get chart versions. Found: " + repoName);
			}
			String indexUrl = repoUrl.endsWith("/") ? repoUrl + "index.yaml" : repoUrl + "/index.yaml";
			if (log.isInfoEnabled()) {
				log.info("Downloading index from {} ...", indexUrl);
			}

			HttpGet httpGet = new HttpGet(indexUrl);
			httpGet.setHeader("User-Agent", "jhelm");

			// Download to byte array to avoid stream lifecycle issues
			byte[] indexData = httpClient.execute(httpGet, (response) -> {
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
		List<ChartVersion> result = new ArrayList<>();
		try (InputStream in = indexIn) {
			// Parse YAML as Map<String,Object>
			Map<?, ?> root = yamlMapper.readValue(in, Map.class);
			Object entriesObj = root.get("entries");
			if (!(entriesObj instanceof Map<?, ?> entries)) {
				return result;
			}
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
		}
		// Helm shows latest first by default for --versions output, so sort descending
		// semver-ish (string fallback)
		result.sort((a, b) -> safeCompareVersions(b.getChartVersion(), a.getChartVersion()));
		return result;
	}

	private int safeCompareVersions(String v1, String v2) {
		if (v1 == null && v2 == null) {
			return 0;
		}
		if (v1 == null) {
			return -1;
		}
		if (v2 == null) {
			return 1;
		}
		// simple split by dots, compare numerically when possible, else lexicographically
		String[] p1 = v1.split("[.-]");
		String[] p2 = v2.split("[.-]");
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

	public void pull(String chartFullName, String repoName, String version, String destDir) throws IOException {
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

		if (chartDigest != null) {
			File cached = getChartCacheFile(chartDigest);
			if (cached.exists()) {
				if (log.isInfoEnabled()) {
					log.info("Using cached chart (digest: {})", chartDigest);
				}
				File destFile = new File(destDir, finalChartName + "-" + version + ".tgz");
				Files.copy(cached.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				untar(destFile, new File(destDir));
				return;
			}
		}

		String tgzFileName = finalChartName + "-" + version + ".tgz";
		pullFromUrl(chartUrl, destDir, tgzFileName);

		// Store in content cache after download
		File downloaded = new File(destDir, tgzFileName);
		if (downloaded.exists()) {
			String actualDigest = computeFileSha256(downloaded);
			File cacheTarget = getChartCacheFile(actualDigest);
			if (!cacheTarget.exists()) {
				Files.copy(downloaded.toPath(), cacheTarget.toPath());
			}
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
		if (chartUrl.startsWith("oci://")) {
			pullOci(chartUrl, destDir, fileName);
			return;
		}
		if (log.isInfoEnabled()) {
			log.info("Pulling chart from {} to {}", chartUrl, destDir);
		}

		File destFile = new File(destDir, fileName);
		destFile.getParentFile().mkdirs();

		HttpGet httpGet = new HttpGet(chartUrl);
		httpGet.setHeader("User-Agent", "jhelm");

		httpClient.execute(httpGet, (response) -> {
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

		// Automatically untar regular charts too
		untar(destFile, new File(destDir));
	}

	public void pullOci(String ociUrl, String destDir, String fileName) throws IOException {
		if (log.isInfoEnabled()) {
			log.info("Pulling OCI chart from {} to {}", ociUrl, destDir);
		}
		String[] ociParts = parseOciUrl(ociUrl);
		String registry = ociParts[0];
		String path = ociParts[1];
		String tag = ociParts[2];

		// 1. Get Token
		String auth = (registryManager != null) ? registryManager.getAuth(registry) : null;
		String token = fetchOciToken(registry, path, auth);

		// 2. Get Manifest
		String manifestUrl = "https://" + registry + "/v2/" + path + "/manifests/" + tag;
		JsonNode manifest;
		try {
			manifest = callOciApi(manifestUrl, token, "application/vnd.oci.image.manifest.v1+json");
		}
		catch (IOException ex) {
			if (log.isWarnEnabled()) {
				log.warn("Failed to get OCI manifest with v1+json, trying without specific accept header: {}",
						ex.getMessage());
			}
			manifest = callOciApi(manifestUrl, token, null);
		}

		// 2b. Resolve OCI image index to a specific manifest if needed
		if (isManifestIndex(manifest)) {
			if (log.isDebugEnabled()) {
				log.debug("OCI manifest is an index, resolving to specific manifest");
			}
			String resolvedDigest = resolveDigestFromIndex(manifest);
			if (resolvedDigest == null) {
				throw new IOException("No suitable manifest found in OCI index for " + ociUrl);
			}
			String specificManifestUrl = "https://" + registry + "/v2/" + path + "/manifests/" + resolvedDigest;
			manifest = callOciApi(specificManifestUrl, token, "application/vnd.oci.image.manifest.v1+json");
		}

		// 3. Find chart layer
		String digest = null;
		if (manifest.has("layers")) {
			for (JsonNode layer : manifest.get("layers")) {
				String mediaType = layer.get("mediaType").asString();
				if ("application/vnd.cncf.helm.chart.content.v1.tar+gzip".equals(mediaType)
						|| "application/vnd.oci.image.layer.v1.tar+gzip".equals(mediaType)) {
					digest = layer.get("digest").asString();
					break;
				}
			}
		}

		if (digest == null) {
			throw new IOException("No chart layer found in OCI manifest for " + ociUrl);
		}

		// 4. Download Layer (blob) — check content cache first
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
		downloadBlob(blobUrl, token, destDir, fileName);

		File downloaded = new File(destDir, fileName);
		if (downloaded.exists()) {
			// Verify digest integrity before caching
			verifyBlobDigest(downloaded, digest);
			File cacheTarget = getChartCacheFile(digest);
			if (!cacheTarget.exists()) {
				Files.copy(downloaded.toPath(), cacheTarget.toPath());
			}
		}

		// 5. Untar it automatically to match helm behavior
		File tgzFile = new File(destDir, fileName);
		untar(tgzFile, new File(destDir));
	}

	String[] parseOciUrl(String ociUrl) throws IOException {
		String raw = ociUrl.substring(6);
		int firstSlash = raw.indexOf('/');
		if (firstSlash == -1) {
			throw new IOException("Invalid OCI URL: " + ociUrl);
		}
		String registry = raw.substring(0, firstSlash);
		String path = raw.substring(firstSlash + 1);
		String tag = "latest";
		if (path.contains(":")) {
			int colon = path.lastIndexOf(':');
			tag = path.substring(colon + 1);
			path = path.substring(0, colon);
		}
		return new String[] { registry, path, tag };
	}

	private String fetchOciToken(String registry, String path, String auth) throws IOException {
		// Standard Docker/OCI bearer token flow.
		// If it's registry-1.docker.io, use auth.docker.io
		String tokenService = registry;
		String tokenUrlPrefix = "https://" + registry + "/v2/token";
		if ("registry-1.docker.io".equals(registry)) {
			tokenUrlPrefix = "https://auth.docker.io/token";
			tokenService = "registry.docker.io";
		}

		String url = tokenUrlPrefix + "?service=" + tokenService + "&scope=repository:" + path + ":pull";
		HttpGet httpGet = new HttpGet(url);
		if (auth != null) {
			httpGet.setHeader("Authorization", "Basic " + auth);
		}

		try {
			return httpClient.execute(httpGet, (response) -> {
				int statusCode = response.getCode();
				if (statusCode != 200) {
					if (log.isWarnEnabled()) {
						log.warn("Failed to fetch OCI token: HTTP {}", statusCode);
					}
					return null;
				}

				HttpEntity entity = response.getEntity();
				if (entity != null) {
					try (InputStream in = entity.getContent()) {
						JsonNode node = jsonMapper.readTree(in);
						return node.has("token") ? node.get("token").asString() : node.get("access_token").asString();
					}
				}
				return null;
			});
		}
		catch (Exception ex) {
			if (log.isWarnEnabled()) {
				log.warn("Failed to parse OCI token: {}", ex.getMessage());
			}
			return null;
		}
	}

	private JsonNode callOciApi(String urlStr, String token, String accept) throws IOException {
		HttpGet httpGet = new HttpGet(urlStr);
		if (token != null) {
			httpGet.setHeader("Authorization", "Bearer " + token);
		}
		if (accept != null) {
			httpGet.setHeader("Accept", accept);
		}

		return httpClient.execute(httpGet, (response) -> {
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				try (InputStream in = entity.getContent()) {
					return jsonMapper.readTree(in);
				}
			}
			return null;
		});
	}

	private void downloadBlob(String urlStr, String token, String destDir, String fileName) throws IOException {
		File destFile = new File(destDir, fileName);
		destFile.getParentFile().mkdirs();

		HttpGet httpGet = new HttpGet(urlStr);
		if (token != null) {
			httpGet.setHeader("Authorization", "Bearer " + token);
		}

		httpClient.execute(httpGet, (response) -> {
			// Handle redirects (common for blobs stored in S3/GCS)
			int status = response.getCode();
			if (status == 301 || status == 302 || status == 307 || status == 308) {
				String newUrl = response.getFirstHeader("Location").getValue();
				downloadBlob(newUrl, null, destDir, fileName); // Redirection might not
																// need the same token if
																// it's a signed URL
				return null;
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
			log.info("OCI Blob downloaded successfully to {}", destFile.getAbsolutePath());
		}
	}

	public void pushOci(String chartTgzPath, String ociUrl) throws IOException {
		if (log.isInfoEnabled()) {
			log.info("Pushing chart {} to {}", chartTgzPath, ociUrl);
		}
		String raw = ociUrl.substring(6);
		int firstSlash = raw.indexOf('/');
		if (firstSlash == -1) {
			throw new IOException("Invalid OCI URL: " + ociUrl);
		}
		String registry = raw.substring(0, firstSlash);
		String path = raw.substring(firstSlash + 1);
		String tag = "latest";
		if (path.contains(":")) {
			int colon = path.lastIndexOf(':');
			tag = path.substring(colon + 1);
			path = path.substring(0, colon);
		}

		String auth = (registryManager != null) ? registryManager.getAuth(registry) : null;
		String token = fetchOciPushToken(registry, path, auth);

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

		if (!blobExists(registry, path, token, chartDigest)) {
			uploadBlob(registry, path, token, chartDigest, chartBytes);
		}
		if (!blobExists(registry, path, token, configDigest)) {
			uploadBlob(registry, path, token, configDigest, configBytes);
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

		pushManifest(registry, path, tag, token, manifestJson.getBytes(StandardCharsets.UTF_8));
		if (log.isInfoEnabled()) {
			log.info("Chart pushed successfully to {}", ociUrl);
		}
	}

	private String fetchOciPushToken(String registry, String path, String auth) throws IOException {
		String tokenService = registry;
		String tokenUrlPrefix = "https://" + registry + "/v2/token";
		if ("registry-1.docker.io".equals(registry)) {
			tokenUrlPrefix = "https://auth.docker.io/token";
			tokenService = "registry.docker.io";
		}

		String url = tokenUrlPrefix + "?service=" + tokenService + "&scope=repository:" + path + ":push,pull";
		HttpGet httpGet = new HttpGet(url);
		if (auth != null) {
			httpGet.setHeader("Authorization", "Basic " + auth);
		}

		try {
			return httpClient.execute(httpGet, (response) -> {
				int statusCode = response.getCode();
				if (statusCode != 200) {
					if (log.isWarnEnabled()) {
						log.warn("Failed to fetch OCI push token: HTTP {}", statusCode);
					}
					return null;
				}
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					try (InputStream in = entity.getContent()) {
						JsonNode node = jsonMapper.readTree(in);
						if (node.has("token")) {
							return node.get("token").asString();
						}
						return node.has("access_token") ? node.get("access_token").asString() : null;
					}
				}
				return null;
			});
		}
		catch (Exception ex) {
			if (log.isWarnEnabled()) {
				log.warn("Failed to parse OCI push token: {}", ex.getMessage());
			}
			return null;
		}
	}

	private boolean blobExists(String registry, String path, String token, String digest) {
		String url = "https://" + registry + "/v2/" + path + "/blobs/" + digest;
		HttpHead head = new HttpHead(url);
		if (token != null) {
			head.setHeader("Authorization", "Bearer " + token);
		}
		try {
			return httpClient.execute(head, (response) -> response.getCode() == 200);
		}
		catch (IOException ex) {
			if (log.isDebugEnabled()) {
				log.debug("Blob existence check failed, assuming absent: {}", ex.getMessage());
			}
			return false;
		}
	}

	private void uploadBlob(String registry, String path, String token, String digest, byte[] content)
			throws IOException {
		String initiateUrl = "https://" + registry + "/v2/" + path + "/blobs/uploads/";
		HttpPost post = new HttpPost(initiateUrl);
		if (token != null) {
			post.setHeader("Authorization", "Bearer " + token);
		}

		String uploadUrl = httpClient.execute(post, (response) -> {
			int status = response.getCode();
			if (status != 202) {
				throw new IOException("Failed to initiate blob upload: HTTP " + status);
			}
			Header location = response.getFirstHeader("Location");
			if (location == null) {
				throw new IOException("No Location header in upload initiation response");
			}
			String loc = location.getValue();
			return loc.startsWith("http") ? loc : "https://" + registry + loc;
		});

		String putUrl = uploadUrl.contains("?") ? uploadUrl + "&digest=" + digest : uploadUrl + "?digest=" + digest;
		HttpPut put = new HttpPut(putUrl);
		if (token != null) {
			put.setHeader("Authorization", "Bearer " + token);
		}
		put.setEntity(new ByteArrayEntity(content, ContentType.APPLICATION_OCTET_STREAM));

		httpClient.execute(put, (response) -> {
			int status = response.getCode();
			if (status != 201) {
				throw new IOException("Failed to upload blob: HTTP " + status);
			}
			return null;
		});
	}

	private void pushManifest(String registry, String path, String tag, String token, byte[] manifest)
			throws IOException {
		String url = "https://" + registry + "/v2/" + path + "/manifests/" + tag;
		HttpPut put = new HttpPut(url);
		if (token != null) {
			put.setHeader("Authorization", "Bearer " + token);
		}
		ContentType manifestType = ContentType.create("application/vnd.oci.image.manifest.v1+json");
		put.setEntity(new ByteArrayEntity(manifest, manifestType));

		httpClient.execute(put, (response) -> {
			int status = response.getCode();
			if (status != 201 && status != 200) {
				throw new IOException("Failed to push manifest: HTTP " + status);
			}
			return null;
		});
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

	public void untar(File tgzFile, File destDir) throws IOException {
		if (log.isInfoEnabled()) {
			log.info("Untarring {} to {}", tgzFile.getAbsolutePath(), destDir.getAbsolutePath());
		}
		try (InputStream fi = Files.newInputStream(tgzFile.toPath());
				InputStream bi = new BufferedInputStream(fi);
				InputStream gzi = new GzipCompressorInputStream(bi);
				TarArchiveInputStream ti = new TarArchiveInputStream(gzi)) {

			TarArchiveEntry entry;
			while ((entry = ti.getNextEntry()) != null) {
				if (!ti.canReadEntryData(entry)) {
					continue;
				}
				File f = new File(destDir, entry.getName());
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
					try (OutputStream o = Files.newOutputStream(f.toPath())) {
						ti.transferTo(o);
					}
				}
			}
		}
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
