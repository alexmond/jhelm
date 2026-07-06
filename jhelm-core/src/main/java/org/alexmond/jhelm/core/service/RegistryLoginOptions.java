package org.alexmond.jhelm.core.service;

/**
 * Transport options for a registry-login handshake, mirroring Helm's
 * {@code registry login} flags. These configure only the login-time validation request
 * (the TLS material and scheme used to authenticate against the registry) and are
 * deliberately <em>not</em> persisted — like Helm, jhelm stores only the credentials, and
 * later OCI operations take their own transport flags.
 *
 * @param caFile path to a CA bundle used to verify the registry's server certificate
 * ({@code --ca-file}), or {@code null}
 * @param certFile path to a client certificate for mutual TLS ({@code --cert-file}), or
 * {@code null}
 * @param keyFile path to the client certificate's private key ({@code --key-file}), or
 * {@code null}
 * @param insecureSkipTlsVerify skip verification of the registry's TLS certificate
 * ({@code --insecure})
 * @param plainHttp contact the registry over plain HTTP instead of HTTPS
 * ({@code --plain-http})
 */
public record RegistryLoginOptions(String caFile, String certFile, String keyFile, boolean insecureSkipTlsVerify,
		boolean plainHttp) {

	/**
	 * Returns options with no TLS material, secure HTTPS transport, and verification on —
	 * the plain default when no login flags are given.
	 * @return the default (no-op) login options
	 */
	public static RegistryLoginOptions none() {
		return new RegistryLoginOptions(null, null, null, false, false);
	}

}
