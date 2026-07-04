package org.alexmond.jhelm.configserver.sample;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the sample config server and checks the three things that make it a useful jhelm
 * fixture and DevOps example: it requires authentication, it serves the profile-merged
 * values, and it hands back {@code {cipher}} tokens verbatim (client-side decryption)
 * rather than decrypting them server-side.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JhelmConfigServerSampleApplicationTest {

	@LocalServerPort
	private int port;

	private final RestClient rest = RestClient.create();

	private String url(String path) {
		return "http://localhost:" + this.port + path;
	}

	@Test
	void testUnauthenticatedRequestIsRejected() {
		assertThrows(HttpClientErrorException.Unauthorized.class,
				() -> this.rest.get().uri(url("/demo/default")).retrieve().toBodilessEntity(), "security is enabled");
	}

	@Test
	void testServesProfileMergedValuesWithEncryptedTokenVerbatim() {
		String body = this.rest.get()
			.uri(url("/demo/prod"))
			.headers((headers) -> headers.setBasicAuth("configuser", "configpass"))
			.retrieve()
			.body(String.class);

		assertNotNull(body);
		assertTrue(body.contains("prod"), "the prod profile overlay is present");
		assertTrue(body.contains("eu-west-1"), "the demo-prod.yml sidecar is merged in");
		assertTrue(body.contains("{cipher}"), "the encrypted secret is served as-is for client-side decryption");
	}

}
