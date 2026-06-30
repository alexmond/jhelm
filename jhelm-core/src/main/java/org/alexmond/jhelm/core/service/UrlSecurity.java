package org.alexmond.jhelm.core.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * SSRF guard for outbound fetches (chart archives, repo indexes). It rejects URLs whose
 * scheme isn't HTTP(S)/OCI and whose host resolves to an address that is never a
 * legitimate chart repository — loopback, link-local (the cloud-metadata {@code
 * 169.254.0.0/16} range), the wildcard address, or multicast.
 *
 * <p>
 * Private/site-local ranges ({@code 10/8}, {@code 172.16/12}, {@code 192.168/16}) are
 * <em>not</em> blocked by default so a CLI pull from a private/internal repo still works;
 * blocking those too is a stricter server-mode policy left for a follow-up.
 */
public final class UrlSecurity {

	private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "oci");

	private UrlSecurity() {
	}

	/**
	 * Validates that {@code uri} is safe to fetch, throwing {@link SecurityException}
	 * (unchecked, so it aborts the fetch) when it is not.
	 * @param uri the URI about to be fetched
	 */
	public static void validateFetchUrl(URI uri) {
		if (uri == null) {
			throw new SecurityException("Refusing to fetch a null URL");
		}
		String scheme = uri.getScheme();
		if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
			throw new SecurityException("Refusing to fetch URL with disallowed scheme '" + scheme + "': " + uri);
		}
		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new SecurityException("Refusing to fetch URL with no host: " + uri);
		}
		InetAddress[] addresses;
		try {
			addresses = InetAddress.getAllByName(host);
		}
		catch (UnknownHostException ex) {
			throw new SecurityException("Refusing to fetch URL with unresolvable host '" + host + "': " + uri);
		}
		for (InetAddress address : addresses) {
			if (isBlocked(address)) {
				throw new SecurityException("Refusing to fetch URL resolving to a non-routable/internal address ("
						+ address.getHostAddress() + "): " + uri);
			}
		}
	}

	private static boolean isBlocked(InetAddress address) {
		// Loopback (127/8, ::1), link-local (169.254/16 cloud metadata, fe80::/10), the
		// wildcard (0.0.0.0, ::) and multicast are never a real chart repo.
		return address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()
				|| address.isMulticastAddress();
	}

}
