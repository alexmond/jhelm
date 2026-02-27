package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;
import org.apache.hc.core5.net.URIBuilder;

/**
 * Network-related functions from Sprig library. Includes DNS lookup and network utility
 * functions.
 *
 * @see <a href="https://masterminds.github.io/sprig/network.html">Sprig Network
 * Functions</a>
 */
public final class NetworkFunctions {

	private NetworkFunctions() {
	}

	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();

		// DNS lookup
		functions.put("getHostByName", getHostByName());

		// URL parsing
		functions.put("urlParse", urlParse());

		return functions;
	}

	// ========== DNS Functions ==========

	/**
	 * Parses a URL into its components: scheme, host, port, path, query.
	 * @return Map of URL components, or empty map on error
	 */
	private static Function urlParse() {
		return (args) -> {
			if (args.length == 0) {
				return Map.of();
			}
			try {
				URIBuilder uriBuilder = new URIBuilder(String.valueOf(args[0]));
				Map<String, Object> result = new HashMap<>();
				result.put("scheme", (uriBuilder.getScheme() != null) ? uriBuilder.getScheme() : "");
				result.put("host", (uriBuilder.getHost() != null) ? uriBuilder.getHost() : "");
				result.put("port", (uriBuilder.getPort() > 0) ? String.valueOf(uriBuilder.getPort()) : "");
				result.put("path", (uriBuilder.getPath() != null) ? uriBuilder.getPath() : "");

				StringBuilder queryString = new StringBuilder();
				if (uriBuilder.getQueryParams() != null && !uriBuilder.getQueryParams().isEmpty()) {
					for (int i = 0; i < uriBuilder.getQueryParams().size(); i++) {
						if (i > 0) {
							queryString.append('&');
						}
						var param = uriBuilder.getQueryParams().get(i);
						queryString.append(param.getName());
						if (param.getValue() != null) {
							queryString.append('=').append(param.getValue());
						}
					}
				}
				result.put("query", queryString.toString());
				return result;
			}
			catch (Exception ex) {
				return Map.of();
			}
		};
	}

	/**
	 * Performs DNS lookup and returns the IP address for a hostname.
	 * @return IP address as string, or empty string on error
	 */
	private static Function getHostByName() {
		return (args) -> {
			if (args.length == 0 || args[0] == null) {
				return "";
			}

			String hostname = String.valueOf(args[0]);
			try {
				InetAddress address = InetAddress.getByName(hostname);
				return address.getHostAddress();
			}
			catch (Exception ex) {
				// Return empty string on DNS resolution failure
				return "";
			}
		};
	}

}
