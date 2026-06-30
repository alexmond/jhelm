package org.alexmond.jhelm.core.service;

import java.net.URI;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlSecurityTest {

	@ParameterizedTest
	@ValueSource(strings = { "https://charts.bitnami.com/bitnami/index.yaml", "http://example.com/charts/app-1.0.0.tgz",
			// a DNS name is NOT resolved here (no network lookup), even an all-hex-ish
			// one,
			// so any name is allowed and the HTTP layer connects normally; a public
			// literal
			// IP is allowed too
			"https://cafe.example.com/x", "https://203.0.113.7/charts/index.yaml" })
	void allowsPublicHttpUrls(String url) {
		assertDoesNotThrow(() -> UrlSecurity.validateFetchUrl(URI.create(url)));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			// cloud metadata (link-local), localhost (loopback), wildcard
			"http://169.254.169.254/latest/meta-data/", "http://127.0.0.1:8080/admin", "https://localhost/index.yaml",
			"https://sub.localhost/index.yaml", "http://0.0.0.0/x",
			// IPv6 literals: loopback and link-local
			"http://[::1]/x", "http://[fe80::1]/x",
			// disallowed schemes
			"file:///etc/passwd", "ftp://example.com/x", "gopher://example.com/x" })
	void blocksSsrfTargetsAndBadSchemes(String url) {
		assertThrows(SecurityException.class, () -> UrlSecurity.validateFetchUrl(URI.create(url)));
	}

}
