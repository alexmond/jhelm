package org.alexmond.jhelm.core.service;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * URL construction and SSRF-guard routing for {@link ConfigServerClient}. Full
 * request/parse round-trips are covered by {@code ConfigServerValuesLoaderTest} (mocked
 * client) and the live-server grounding; a real HTTP round-trip here is impossible
 * because the SSRF guard refuses loopback.
 */
class ConfigServerClientTest {

	private static ConfigServerRequest request(String uri, List<String> profiles, String label) {
		return new ConfigServerRequest(uri, "myapp", profiles, label, null, null, null);
	}

	@Test
	void testUrlWithProfilesAndLabel() {
		assertEquals("http://cfg/myapp/prod,eu/main",
				ConfigServerClient.buildUrl(request("http://cfg", List.of("prod", "eu"), "main")));
	}

	@Test
	void testUrlOmitsLabelWhenAbsent() {
		assertEquals("http://cfg/myapp/prod",
				ConfigServerClient.buildUrl(request("http://cfg", List.of("prod"), null)));
	}

	@Test
	void testUrlFallsBackToDefaultProfile() {
		assertEquals("http://cfg/myapp/default", ConfigServerClient.buildUrl(request("http://cfg", List.of(), null)));
	}

	@Test
	void testUrlStripsTrailingSlash() {
		assertEquals("http://cfg/myapp/prod",
				ConfigServerClient.buildUrl(request("http://cfg/", List.of("prod"), null)));
	}

	@Test
	void testFetchIsSsrfGuarded() {
		// The fetch must ride the SSRF-guarded path (not the unguarded java.net.http
		// one): a
		// non-routable target is refused at the guard before any connection. The wildcard
		// address is always blocked, regardless of block-private-networks.
		ConfigServerClient client = new ConfigServerClient(new RepoManager(null, false, false));
		ConfigServerRequest req = request("http://0.0.0.0:8888", List.of("default"), null);
		assertThrows(SecurityException.class, () -> client.fetch(req));
	}

	@Test
	void testGuardValidatesTheBuiltUrl() {
		// A routable host passes the guard the client uses (it would then simply
		// connect),
		// confirming the URL the client builds is what gets validated.
		ConfigServerRequest req = request("http://config.example.com:8888", List.of("prod"), "main");
		assertDoesNotThrow(() -> UrlSecurity.validateFetchUrl(URI.create(ConfigServerClient.buildUrl(req)), true));
	}

}
