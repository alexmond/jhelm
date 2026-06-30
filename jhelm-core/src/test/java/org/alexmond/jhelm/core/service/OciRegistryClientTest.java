package org.alexmond.jhelm.core.service;

import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

}
