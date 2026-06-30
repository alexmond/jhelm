package org.alexmond.jhelm.core.service;

import java.net.URI;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlSecurityTest {

	@ParameterizedTest
	@ValueSource(
			strings = { "https://charts.bitnami.com/bitnami/index.yaml", "http://example.com/charts/app-1.0.0.tgz" })
	void allowsPublicHttpUrls(String url) {
		assertDoesNotThrow(() -> UrlSecurity.validateFetchUrl(URI.create(url)));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			// cloud metadata (link-local), localhost (loopback), wildcard
			"http://169.254.169.254/latest/meta-data/", "http://127.0.0.1:8080/admin", "https://localhost/index.yaml",
			"http://0.0.0.0/x",
			// disallowed schemes
			"file:///etc/passwd", "ftp://example.com/x", "gopher://example.com/x" })
	void blocksSsrfTargetsAndBadSchemes(String url) {
		assertThrows(SecurityException.class, () -> UrlSecurity.validateFetchUrl(URI.create(url)));
	}

}
