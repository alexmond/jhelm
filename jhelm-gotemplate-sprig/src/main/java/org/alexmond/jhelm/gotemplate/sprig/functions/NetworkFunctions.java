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

		// URL parsing and building
		functions.put("urlParse", urlParse());
		functions.put("urlJoin", urlJoin());

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
	 * Constructs a URL from a dictionary of components. Accepts keys: scheme, host, port,
	 * path, query, fragment, userinfo.
	 * @return The constructed URL string
	 */
	@SuppressWarnings("unchecked")
	private static Function urlJoin() {
		return (args) -> {
			if (args.length == 0 || args[0] == null || !(args[0] instanceof Map)) {
				return "";
			}
			Map<String, Object> components = (Map<String, Object>) args[0];
			StringBuilder url = new StringBuilder();

			String scheme = getStr(components, "scheme");
			String host = getStr(components, "host");
			String port = getStr(components, "port");
			String path = getStr(components, "path");
			String query = getStr(components, "query");
			String fragment = getStr(components, "fragment");
			String userinfo = getStr(components, "userinfo");

			if (!scheme.isEmpty()) {
				url.append(scheme).append("://");
			}
			if (!userinfo.isEmpty()) {
				url.append(userinfo).append('@');
			}
			url.append(host);
			if (!port.isEmpty()) {
				url.append(':').append(port);
			}
			if (!path.isEmpty()) {
				if (!path.startsWith("/") && !host.isEmpty()) {
					url.append('/');
				}
				url.append(path);
			}
			if (!query.isEmpty()) {
				url.append('?').append(query);
			}
			if (!fragment.isEmpty()) {
				url.append('#').append(fragment);
			}
			return url.toString();
		};
	}

	private static String getStr(Map<String, Object> map, String key) {
		Object val = map.get(key);
		return (val != null) ? String.valueOf(val) : "";
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
