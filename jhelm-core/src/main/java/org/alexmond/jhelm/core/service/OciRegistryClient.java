package org.alexmond.jhelm.core.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Low-level OCI (Open Container Initiative) registry client for pulling and pushing Helm
 * chart blobs via the OCI distribution API.
 */
@Slf4j
class OciRegistryClient {

	private final CloseableHttpClient httpClient;

	private final JsonMapper jsonMapper;

	OciRegistryClient(CloseableHttpClient httpClient) {
		this.httpClient = httpClient;
		this.jsonMapper = JsonMapper.builder().build();
	}

	/**
	 * Parses an {@code oci://} URL into registry, path, and tag components.
	 * @param ociUrl the full OCI URL (e.g. {@code oci://registry.io/org/chart:1.0.0})
	 * @return array of [registry, path, tag]
	 */
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

	/**
	 * Fetches a bearer token from the registry's token endpoint.
	 * @param registry the registry hostname
	 * @param path the repository path
	 * @param auth optional Basic auth credentials
	 * @param scope the requested scope (e.g. {@code "pull"} or {@code "push,pull"})
	 * @return the bearer token, or {@code null} if unavailable
	 */
	String fetchToken(String registry, String path, String auth, String scope) throws IOException {
		String tokenService = registry;
		String tokenUrlPrefix = "https://" + registry + "/v2/token";
		if ("registry-1.docker.io".equals(registry)) {
			tokenUrlPrefix = "https://auth.docker.io/token";
			tokenService = "registry.docker.io";
		}

		String url = tokenUrlPrefix + "?service=" + tokenService + "&scope=repository:" + path + ":" + scope;
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
				log.warn("Failed to parse OCI token: {}", ex.getMessage());
			}
			return null;
		}
	}

	/**
	 * Fetches a manifest from the registry.
	 * @param manifestUrl full URL to the manifest endpoint
	 * @param token bearer token for authentication
	 * @param accept optional Accept header value
	 * @return the manifest as a JSON tree
	 */
	JsonNode getManifest(String manifestUrl, String token, String accept) throws IOException {
		HttpGet httpGet = new HttpGet(manifestUrl);
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

	/**
	 * Checks whether a manifest represents an OCI image index (multi-platform).
	 */
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

	/**
	 * Resolves the best manifest digest from an OCI image index.
	 */
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

	/**
	 * Downloads a blob to a local file, following redirects.
	 */
	void downloadBlob(String urlStr, String token, File destFile) throws IOException {
		destFile.getParentFile().mkdirs();

		HttpGet httpGet = new HttpGet(urlStr);
		if (token != null) {
			httpGet.setHeader("Authorization", "Bearer " + token);
		}

		httpClient.execute(httpGet, (response) -> {
			int status = response.getCode();
			if (status == 301 || status == 302 || status == 307 || status == 308) {
				String newUrl = response.getFirstHeader("Location").getValue();
				downloadBlob(newUrl, null, destFile);
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

	/**
	 * Checks whether a blob already exists in the registry.
	 */
	boolean blobExists(String registry, String path, String token, String digest) {
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

	/**
	 * Uploads a blob to the registry using the two-step initiate + PUT flow.
	 */
	void uploadBlob(String registry, String path, String token, String digest, byte[] content) throws IOException {
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

	/**
	 * Pushes a manifest to the registry.
	 */
	void pushManifest(String registry, String path, String tag, String token, byte[] manifest) throws IOException {
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

}
