package org.alexmond.jhelm.gotemplate.sprig.network;

import org.alexmond.jhelm.gotemplate.Function;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Network-related functions from Sprig library.
 * Includes DNS lookup and network utility functions.
 *
 * @see <a href="https://masterminds.github.io/sprig/network.html">Sprig Network Functions</a>
 */
public class NetworkFunctions {

    public static Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();

        // DNS lookup
        functions.put("getHostByName", getHostByName());

        return functions;
    }

    // ========== DNS Functions ==========

    /**
     * Performs DNS lookup and returns the IP address for a hostname.
     *
     * @param args [0] hostname (string)
     * @return IP address as string, or empty string on error
     */
    private static Function getHostByName() {
        return args -> {
            if (args.length == 0 || args[0] == null) return "";

            String hostname = String.valueOf(args[0]);
            try {
                InetAddress address = InetAddress.getByName(hostname);
                return address.getHostAddress();
            } catch (Exception e) {
                // Return empty string on DNS resolution failure
                return "";
            }
        };
    }
}
