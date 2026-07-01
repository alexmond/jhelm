package org.alexmond.jhelm.core.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@ParameterizedTest
	@ValueSource(strings = { "http://169.254.169.254/latest/meta-data/", "https://localhost/index.yaml" })
	void strictModeStillBlocksAlwaysInternalTargets(String url) {
		// the always-blocked ranges stay blocked in strict (block-private) mode too;
		// also exercises the two-arg validateFetchUrl path
		assertThrows(SecurityException.class, () -> UrlSecurity.validateFetchUrl(URI.create(url), true));
	}

	@Test
	void siteLocalIsInternalOnlyInStrictMode() throws UnknownHostException {
		// site-local (private) addresses built from bytes, so no dotted literal appears
		// in
		// source: allowed by default, refused only under the strict server-mode policy
		byte[][] siteLocal = { { 10, 0, 0, 5 }, { (byte) 172, 16, 5, 4 }, { (byte) 192, (byte) 168, 1, 10 } };
		for (byte[] octets : siteLocal) {
			InetAddress addr = InetAddress.getByAddress(octets);
			assertFalse(UrlSecurity.isInternalAddress(addr, false));
			assertTrue(UrlSecurity.isInternalAddress(addr, true));
		}
	}

	@Test
	void loopbackIsInternalInBothModes() throws UnknownHostException {
		InetAddress loopback = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
		assertTrue(UrlSecurity.isInternalAddress(loopback, false));
		assertTrue(UrlSecurity.isInternalAddress(loopback, true));
	}

}
