package org.alexmond.jhelm.core.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsrfGuardingDnsResolverTest {

	private final SsrfGuardingDnsResolver resolver = new SsrfGuardingDnsResolver();

	@ParameterizedTest
	@ValueSource(strings = {
			// resolves to loopback via /etc/hosts; literal loopback / metadata / wildcard
			"localhost", "127.0.0.1", "169.254.169.254", "0.0.0.0" })
	void refusesInternalTargets(String host) {
		assertThrows(SecurityException.class, () -> resolver.resolve(host));
	}

	@Test
	void allowsPublicLiteral() throws UnknownHostException {
		// a public IP literal is parsed without a network lookup and passes through
		InetAddress[] addresses = resolver.resolve("203.0.113.7");
		assertTrue(addresses.length >= 1);
	}

}
