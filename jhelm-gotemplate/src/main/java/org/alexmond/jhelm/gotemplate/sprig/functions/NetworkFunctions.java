package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;

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

		return functions;
	}

	// ========== DNS Functions ==========

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
