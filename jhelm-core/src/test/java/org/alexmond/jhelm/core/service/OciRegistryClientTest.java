package org.alexmond.jhelm.core.service;

import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OciRegistryClientTest {

	@TempDir
	Path tempDir;

	private final OciRegistryClient client = new OciRegistryClient(mock(CloseableHttpClient.class));

	@Test
	void testParseOciUrlWithTag() throws IOException {
		String[] parts = client.parseOciUrl("oci://my.registry.io/charts/mychart:1.2.3");
		assertEquals("my.registry.io", parts[0]);
		assertEquals("charts/mychart", parts[1]);
		assertEquals("1.2.3", parts[2]);
	}

	@Test
	void testParseOciUrlDefaultsToLatest() throws IOException {
		String[] parts = client.parseOciUrl("oci://my.registry.io/charts/mychart");
		assertEquals("my.registry.io", parts[0]);
		assertEquals("charts/mychart", parts[1]);
		assertEquals("latest", parts[2]);
	}

	@Test
	void testParseOciUrlInvalid() {
		assertThrows(IOException.class, () -> client.parseOciUrl("oci://nopath"));
	}

	@Test
	void testIsManifestIndexDetectsIndex() throws Exception {
		JsonMapper mapper = JsonMapper.builder().build();

		JsonNode index = mapper.readTree("""
				{"mediaType":"application/vnd.oci.image.index.v1+json","manifests":[{"digest":"sha256:abc"}]}
				""");
		assertTrue(client.isManifestIndex(index));

		JsonNode singleManifest = mapper.readTree("""
				{"mediaType":"application/vnd.oci.image.manifest.v1+json","layers":[]}
				""");
		assertFalse(client.isManifestIndex(singleManifest));
	}

	@Test
	void testResolveDigestFromIndexPrefersNoPlatform() throws Exception {
		JsonMapper mapper = JsonMapper.builder().build();

		JsonNode index = mapper.readTree("""
				{"manifests":[
				  {"digest":"sha256:amd64","platform":{"os":"linux","architecture":"amd64"}},
				  {"digest":"sha256:noplat"}
				]}
				""");
		assertEquals("sha256:noplat", client.resolveDigestFromIndex(index));
	}

	@Test
	void testResolveDigestFromIndexPrefersLinuxAmd64() throws Exception {
		JsonMapper mapper = JsonMapper.builder().build();

		JsonNode index = mapper.readTree("""
				{"manifests":[
				  {"digest":"sha256:arm64","platform":{"os":"linux","architecture":"arm64"}},
				  {"digest":"sha256:amd64","platform":{"os":"linux","architecture":"amd64"}}
				]}
				""");
		assertEquals("sha256:amd64", client.resolveDigestFromIndex(index));
	}

	@Test
	void downloadBlobRejectsInternalInitialUrl() {
		// SSRF: a blob URL targeting the cloud-metadata IP is refused before any request
		assertThrows(SecurityException.class,
				() -> client.downloadBlob("http://169.254.169.254/v2/blob", null, tempDir.resolve("b").toFile()));
	}

	@Test
	void downloadBlobRejectsDisallowedScheme() {
		assertThrows(SecurityException.class,
				() -> client.downloadBlob("file:///etc/passwd", null, tempDir.resolve("b").toFile()));
	}

	@Test
	@SuppressWarnings("unchecked")
	void downloadBlobRejectsRedirectWithNoLocation() throws Exception {
		// A 3xx with a missing Location header must fail cleanly, not NPE
		CloseableHttpClient http = mock(CloseableHttpClient.class);
		ClassicHttpResponse response = mock(ClassicHttpResponse.class);
		when(response.getCode()).thenReturn(302);
		when(response.getFirstHeader("Location")).thenReturn(null);
		when(http.execute(any(HttpUriRequest.class), any(HttpClientResponseHandler.class)))
			.thenAnswer((inv) -> inv.getArgument(1, HttpClientResponseHandler.class).handleResponse(response));
		OciRegistryClient redirectClient = new OciRegistryClient(http);
		assertThrows(IOException.class, () -> redirectClient.downloadBlob("https://registry.example.com/v2/blob", "tok",
				tempDir.resolve("b").toFile()));
	}

	@Test
	void fetchTokenReturnsTokenFromSuccessBody() throws Exception {
		OciRegistryClient c = clientReturning(jsonResponse(200, "{\"token\":\"secret-token\"}"));
		assertEquals("secret-token", c.fetchToken("my.registry.io", "charts/mychart", "dXNlcjpwYXNz", "pull"));
	}

	@Test
	void fetchTokenFallsBackToAccessToken() throws Exception {
		OciRegistryClient c = clientReturning(jsonResponse(200, "{\"access_token\":\"oauth-token\"}"));
		assertEquals("oauth-token", c.fetchToken("my.registry.io", "charts/mychart", null, "pull"));
	}

	@Test
	void fetchTokenReturnsNullWhenBodyHasNoToken() throws Exception {
		OciRegistryClient c = clientReturning(jsonResponse(200, "{}"));
		assertNull(c.fetchToken("my.registry.io", "charts/mychart", null, "pull"));
	}

	@Test
	void fetchTokenReturnsNullOnNon200() throws Exception {
		OciRegistryClient c = clientReturning(jsonResponse(401, null));
		assertNull(c.fetchToken("my.registry.io", "charts/mychart", null, "pull"));
	}

	@Test
	void fetchTokenUsesDockerAuthEndpointAndReturnsToken() throws Exception {
		// registry-1.docker.io routes to the auth.docker.io token service
		OciRegistryClient c = clientReturning(jsonResponse(200, "{\"token\":\"docker-token\"}"));
		assertEquals("docker-token", c.fetchToken("registry-1.docker.io", "library/nginx", null, "pull"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void fetchTokenReturnsNullWhenRequestThrows() throws Exception {
		CloseableHttpClient http = mock(CloseableHttpClient.class);
		when(http.execute(any(HttpUriRequest.class), any(HttpClientResponseHandler.class)))
			.thenThrow(new IOException("connection reset"));
		OciRegistryClient c = new OciRegistryClient(http);
		assertNull(c.fetchToken("my.registry.io", "charts/mychart", null, "pull"));
	}

	@Test
	void getManifestParsesBodyWithTokenAndAccept() throws Exception {
		OciRegistryClient c = clientReturning(jsonResponse(200, "{\"schemaVersion\":2}"));
		JsonNode manifest = c.getManifest("https://my.registry.io/v2/charts/mychart/manifests/1.0.0", "bearer",
				"application/vnd.oci.image.manifest.v1+json");
		assertEquals(2, manifest.get("schemaVersion").asInt());
	}

	@Test
	void getManifestReturnsNullWhenNoEntity() throws Exception {
		OciRegistryClient c = clientReturning(jsonResponse(200, null));
		assertNull(c.getManifest("https://my.registry.io/v2/charts/mychart/manifests/1.0.0", null, null));
	}

	@Test
	void resolveDigestFromIndexReturnsNullForEmptyManifests() throws Exception {
		JsonMapper mapper = JsonMapper.builder().build();
		assertNull(client.resolveDigestFromIndex(mapper.readTree("{\"manifests\":[]}")));
		assertNull(client.resolveDigestFromIndex(mapper.readTree("{}")));
	}

	@Test
	void resolveDigestFromIndexFallsBackToFirstEntry() throws Exception {
		// no platform-agnostic entry and no linux/amd64 — fall back to the first manifest
		JsonMapper mapper = JsonMapper.builder().build();
		JsonNode index = mapper.readTree("""
				{"manifests":[
				  {"digest":"sha256:win","platform":{"os":"windows","architecture":"amd64"}},
				  {"digest":"sha256:arm","platform":{"os":"linux","architecture":"arm64"}}
				]}
				""");
		assertEquals("sha256:win", client.resolveDigestFromIndex(index));
	}

	@Test
	void blobExistsTrueOn200() throws Exception {
		OciRegistryClient c = clientReturning(jsonResponse(200, null));
		assertTrue(c.blobExists("my.registry.io", "charts/mychart", "bearer", "sha256:abc"));
	}

	@Test
	void blobExistsFalseOnNon200() throws Exception {
		OciRegistryClient c = clientReturning(jsonResponse(404, null));
		assertFalse(c.blobExists("my.registry.io", "charts/mychart", null, "sha256:abc"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void blobExistsFalseWhenRequestThrows() throws Exception {
		CloseableHttpClient http = mock(CloseableHttpClient.class);
		when(http.execute(any(HttpUriRequest.class), any(HttpClientResponseHandler.class)))
			.thenThrow(new IOException("head failed"));
		OciRegistryClient c = new OciRegistryClient(http);
		assertFalse(c.blobExists("my.registry.io", "charts/mychart", null, "sha256:abc"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void downloadBlobFailsWhenRedirectChainExceedsCap() throws Exception {
		// Every response is a 302 to another valid public host, so the redirect cap
		// trips.
		CloseableHttpClient http = mock(CloseableHttpClient.class);
		ClassicHttpResponse response = mock(ClassicHttpResponse.class);
		when(response.getCode()).thenReturn(302);
		when(response.getFirstHeader("Location"))
			.thenReturn(new BasicHeader("Location", "https://cdn.example.com/next"));
		when(http.execute(any(HttpUriRequest.class), any(HttpClientResponseHandler.class)))
			.thenAnswer((inv) -> inv.getArgument(1, HttpClientResponseHandler.class).handleResponse(response));
		OciRegistryClient c = new OciRegistryClient(http);
		IOException ex = assertThrows(IOException.class,
				() -> c.downloadBlob("https://registry.example.com/v2/blob", "tok", tempDir.resolve("b").toFile()));
		assertTrue(ex.getMessage().contains("Too many redirects"));
	}

	/** Builds a mocked response with the given status and optional JSON body. */
	private ClassicHttpResponse jsonResponse(int code, String body) throws Exception {
		ClassicHttpResponse response = mock(ClassicHttpResponse.class);
		when(response.getCode()).thenReturn(code);
		if (body != null) {
			HttpEntity entity = mock(HttpEntity.class);
			when(entity.getContent()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
			when(response.getEntity()).thenReturn(entity);
		}
		else {
			when(response.getEntity()).thenReturn(null);
		}
		return response;
	}

	/**
	 * Builds a client whose HTTP execute runs the caller's handler against
	 * {@code response}.
	 */
	@SuppressWarnings("unchecked")
	private OciRegistryClient clientReturning(ClassicHttpResponse response) throws Exception {
		CloseableHttpClient http = mock(CloseableHttpClient.class);
		when(http.execute(any(HttpUriRequest.class), any(HttpClientResponseHandler.class)))
			.thenAnswer((inv) -> inv.getArgument(1, HttpClientResponseHandler.class).handleResponse(response));
		return new OciRegistryClient(http);
	}

}
