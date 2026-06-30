package org.alexmond.jhelm.core.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SSRF guard for outbound fetches (chart archives, repo indexes). It rejects URLs whose
 * scheme isn't HTTP(S)/OCI, that target {@code localhost}, or whose host is a numeric IP
 * literal in a range that never names a legitimate chart repository — loopback,
 * link-local (the cloud-metadata {@code 169.254.0.0/16} range), the wildcard address, or
 * multicast.
 *
 * <p>
 * The guard deliberately does <em>not</em> resolve DNS names. An eager lookup on the
 * fetch hot path is an unbounded blocking call (a hang/DoS risk with no socket timeout),
 * and because the HTTP client resolves the host independently when it connects, a lookup
 * here can't reliably close the DNS-rebinding hole anyway — only pinning the resolved
 * address at connection time would, which is a stricter server-mode policy left for a
 * follow-up. So a host given as a literal internal IP is blocked, but a <em>name</em>
 * that resolves to one is left for the HTTP layer.
 *
 * <p>
 * Private/site-local ranges ({@code 10/8}, {@code 172.16/12}, {@code 192.168/16}) are
 * also <em>not</em> blocked by default so a CLI pull from a private/internal repo still
 * works; blocking those too is part of the same stricter server-mode policy.
 */
public final class UrlSecurity {

	private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "oci");

	/**
	 * IPv6 literals are the only hostnames made up purely of hex digits, colons and dots.
	 */
	private static final Pattern IPV6_LITERAL_CHARS = Pattern.compile("^[0-9A-Fa-f:.]+$");

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
		String lower = host.toLowerCase(Locale.ROOT);
		if (lower.equals("localhost") || lower.endsWith(".localhost")) {
			throw new SecurityException("Refusing to fetch URL targeting localhost: " + uri);
		}
		InetAddress literal = parseIpLiteral(host);
		if (literal != null && isBlocked(literal)) {
			throw new SecurityException("Refusing to fetch URL targeting a non-routable/internal address ("
					+ literal.getHostAddress() + "): " + uri);
		}
	}

	/**
	 * Parses {@code host} as a numeric IP literal without any network lookup, returning
	 * {@code null} when it is a DNS name (which is intentionally not resolved here).
	 * @param host the URI host, possibly bracketed for IPv6
	 * @return the parsed address, or {@code null} if {@code host} is not an IP literal
	 */
	private static InetAddress parseIpLiteral(String host) {
		String h = host;
		if (h.length() > 1 && h.charAt(0) == '[' && h.charAt(h.length() - 1) == ']') {
			h = h.substring(1, h.length() - 1);
		}
		boolean ipv6 = h.indexOf(':') >= 0 && IPV6_LITERAL_CHARS.matcher(h).matches();
		if (!ipv6 && !isIpv4Literal(h)) {
			return null;
		}
		try {
			// Safe: a syntactically valid IP literal is parsed by getByName with no DNS
			// query.
			return InetAddress.getByName(h);
		}
		catch (UnknownHostException ex) {
			return null;
		}
	}

	private static boolean isIpv4Literal(String h) {
		String[] parts = h.split("\\.", -1);
		if (parts.length != 4) {
			return false;
		}
		for (String part : parts) {
			if (part.isEmpty() || part.length() > 3) {
				return false;
			}
			int value;
			try {
				value = Integer.parseInt(part);
			}
			catch (NumberFormatException ex) {
				return false;
			}
			if (value < 0 || value > 255) {
				return false;
			}
		}
		return true;
	}

	private static boolean isBlocked(InetAddress address) {
		// Loopback (127/8, ::1), link-local (169.254/16 cloud metadata, fe80::/10), the
		// wildcard (0.0.0.0, ::) and multicast are never a real chart repo.
		return address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()
				|| address.isMulticastAddress();
	}

}
