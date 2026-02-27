package org.alexmond.jhelm.gotemplate.helm.functions;

import java.util.HashMap;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;

/**
 * Helm chart-specific helper functions Based on: <a href=
 * "https://helm.sh/docs/chart_template_guide/function_list/">https://helm.sh/docs/chart_template_guide/function_list/</a>
 */
public final class ChartFunctions {

	private ChartFunctions() {
	}

	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();

		// Certificate generation stubs (for Bitnami charts compatibility)
		functions.put("buildCustomCert", buildCustomCert());
		functions.put("genCA", genCA());
		functions.put("genSelfSignedCert", genSelfSignedCert());
		functions.put("genSignedCert", genSignedCert());
		functions.put("genPrivateKey", genPrivateKey());

		return functions;
	}

	/**
	 * buildCustomCert generates a custom TLS certificate Stub implementation for Bitnami
	 * charts compatibility Returns a map with Cert and Key fields
	 */
	private static Function buildCustomCert() {
		return (args) -> {
			Map<String, String> cert = new HashMap<>();
			cert.put("Cert", "-----BEGIN CERTIFICATE-----\n...stub...\n-----END CERTIFICATE-----");
			cert.put("Key", "-----BEGIN PRIVATE KEY-----\n...stub...\n-----END PRIVATE KEY-----");
			return cert;
		};
	}

	/**
	 * genCA generates a new Certificate Authority Stub implementation - returns
	 * placeholder certificate
	 */
	private static Function genCA() {
		return (args) -> {
			Map<String, String> ca = new HashMap<>();
			ca.put("Cert", "-----BEGIN CERTIFICATE-----\nMIIC...CA_STUB...==\n-----END CERTIFICATE-----");
			ca.put("Key", "-----BEGIN RSA PRIVATE KEY-----\nMIIE...CA_KEY_STUB...==\n-----END RSA PRIVATE KEY-----");
			return ca;
		};
	}

	/**
	 * genSelfSignedCert generates a self-signed certificate Stub implementation
	 */
	private static Function genSelfSignedCert() {
		return (args) -> {
			Map<String, String> cert = new HashMap<>();
			cert.put("Cert", "-----BEGIN CERTIFICATE-----\nMIIC...SELF_SIGNED_STUB...==\n-----END CERTIFICATE-----");
			cert.put("Key",
					"-----BEGIN RSA PRIVATE KEY-----\nMIIE...SELF_SIGNED_KEY_STUB...==\n-----END RSA PRIVATE KEY-----");
			return cert;
		};
	}

	/**
	 * genSignedCert generates a certificate signed by a CA Stub implementation Args: cn,
	 * ips, alternateIPs, daysValid, ca
	 */
	private static Function genSignedCert() {
		return (args) -> {
			Map<String, String> cert = new HashMap<>();
			cert.put("Cert", "-----BEGIN CERTIFICATE-----\nMIIC...SIGNED_STUB...==\n-----END CERTIFICATE-----");
			cert.put("Key",
					"-----BEGIN RSA PRIVATE KEY-----\nMIIE...SIGNED_KEY_STUB...==\n-----END RSA PRIVATE KEY-----");
			return cert;
		};
	}

	/**
	 * genPrivateKey generates a new private key Stub implementation
	 */
	private static Function genPrivateKey() {
		return (args) -> "-----BEGIN RSA PRIVATE KEY-----\nMIIE...PRIVATE_KEY_STUB...==\n-----END RSA PRIVATE KEY-----";
	}

}
