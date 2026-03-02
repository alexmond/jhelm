package org.alexmond.jhelm.core.service;

import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;

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

import tools.jackson.databind.JsonNode;

import org.alexmond.jhelm.core.model.RepositoryConfig;

@Slf4j
public class RepoManager {

	private final YAMLMapper yamlMapper;

	private final String configPath;

	private CloseableHttpClient httpClient;

	private RepoHttpClientFactory httpClientFactory;

	private OciRegistryClient ociClient;

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
		this.httpClientFactory = new RepoHttpClientFactory(client, insecureSkipTlsVerify);
		this.ociClient = new OciRegistryClient(client);
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
			httpClient = HttpClients.createDefault();
		}
		catch (Exception ex) {
			if (log.isErrorEnabled()) {
				log.error("Failed to initialize HTTP client", ex);
			}
			httpClient = HttpClients.createDefault();
		}
		httpClientFactory = new RepoHttpClientFactory(httpClient, insecureSkipTlsVerify);
		ociClient = new OciRegistryClient(httpClient);
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
		RepositoryConfig config = loadConfig();
		config.getRepositories().removeIf((r) -> r.getName().equals(name));
		RepositoryConfig.Repository repo = RepositoryConfig.Repository.builder().name(name).url(url).build();
		config.getRepositories().add(repo);
		config.setGenerated(OffsetDateTime.now().toString());
		saveConfig(config);
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
		File indexFile = getIndexCacheFile(repoName);
		InputStream indexIn;
		if (indexFile.exists()) {
			indexIn = new FileInputStream(indexFile);
		}
		else {
			RepositoryConfig.Repository repo = getRepository(repoName);
			String repoUrl = (repo != null) ? repo.getUrl() : null;
			if (repoUrl == null) {
				throw new IOException("Repository name is required to get chart versions. Found: " + repoName);
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
		List<ChartVersion> result = new ArrayList<>();
		try (InputStream in = indexIn) {
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
		pullFromUrl(chartUrl, destDir, tgzFileName, repo);

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
		pullFromUrl(chartUrl, destDir, fileName, null);
	}

	void pullFromUrl(String chartUrl, String destDir, String fileName, RepositoryConfig.Repository repo)
			throws IOException {
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
