package org.alexmond.jhelm.core.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.hc.client5.http.SystemDefaultDnsResolver;

/**
 * A {@link org.apache.hc.client5.http.DnsResolver} that refuses to hand back any address
 * pointing at a non-routable/internal target, closing the SSRF gap a URL-only check
 * can't: a host given as a DNS <em>name</em> that resolves to loopback or the
 * cloud-metadata range ({@code 169.254.169.254}) is rejected here. Because resolution
 * happens once in the resolver and the connection is pinned to the exact addresses it
 * returns, there is no resolve-then-reconnect (DNS-rebinding/TOCTOU) window.
 *
 * <p>
 * This is the resolution the HTTP client already performs at connect time, so it adds no
 * extra lookup; {@link UrlSecurity} still rejects bad schemes, {@code localhost} and
 * literal internal IPs up front before a connection is ever opened.
 */
final class SsrfGuardingDnsResolver extends SystemDefaultDnsResolver {

	@Override
	public InetAddress[] resolve(String host) throws UnknownHostException {
		InetAddress[] addresses = super.resolve(host);
		for (InetAddress address : addresses) {
			if (UrlSecurity.isInternalAddress(address)) {
				throw new SecurityException("Refusing to connect to non-routable/internal address ("
						+ address.getHostAddress() + ") resolved from host '" + host + "'");
			}
		}
		return addresses;
	}

}
