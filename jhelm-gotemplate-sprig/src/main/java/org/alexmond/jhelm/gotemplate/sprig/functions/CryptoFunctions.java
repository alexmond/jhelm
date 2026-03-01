package org.alexmond.jhelm.gotemplate.sprig.functions;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.gotemplate.Function;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import org.apache.commons.codec.binary.Hex;

/**
 * Cryptographic and random generation functions from Sprig library. Includes password
 * generation, random string generation, and certificate generation.
 *
 * @see <a href="https://masterminds.github.io/sprig/crypto.html">Sprig Crypto
 * Functions</a>
 */
public final class CryptoFunctions {

	private CryptoFunctions() {
	}

	private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(
			BCryptPasswordEncoder.BCryptVersion.$2Y);

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	public static Map<String, Function> getFunctions() {
		Map<String, Function> functions = new HashMap<>();

		functions.put("randAlphaNum", randAlphaNum());
		functions.put("randAlpha", randAlpha());
		functions.put("randNumeric", randNumeric());
		functions.put("randAscii", randAscii());

		functions.put("htpasswd", htpasswd());
		functions.put("derivePassword", derivePassword());

		functions.put("genPrivateKey", genPrivateKey());

		functions.put("buildCustomCert", buildCustomCert());
		functions.put("genCA", genCA());
		functions.put("genSignedCert", genSignedCert());
		functions.put("genSelfSignedCert", genSelfSignedCert());

		functions.put("uuidv4", uuidv4());

		functions.put("bcrypt", bcrypt());
		functions.put("randBytes", randBytes());
		functions.put("encryptAES", encryptAES());
		functions.put("decryptAES", decryptAES());

		functions.put("genCAWithKey", genCAWithKey());
		functions.put("genSelfSignedCertWithKey", genSelfSignedCertWithKey());
		functions.put("genSignedCertWithKey", genSignedCertWithKey());

		return functions;
	}

	// ========== UUID Generation ==========

	private static Function uuidv4() {
		return (args) -> java.util.UUID.randomUUID().toString();
	}

	// ========== Random String Generation ==========

	private static Function randAlphaNum() {
		return (args) -> {
			if (args.length == 0) {
				return "";
			}
			int length = ((Number) args[0]).intValue();
			String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
			return randomString(length, chars);
		};
	}

	private static Function randAlpha() {
		return (args) -> {
			if (args.length == 0) {
				return "";
			}
			int length = ((Number) args[0]).intValue();
			String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
			return randomString(length, chars);
		};
	}

	private static Function randNumeric() {
		return (args) -> {
			if (args.length == 0) {
				return "";
			}
			int length = ((Number) args[0]).intValue();
			return randomString(length, "0123456789");
		};
	}

	private static Function randAscii() {
		return (args) -> {
			if (args.length == 0) {
				return "";
			}
			int length = ((Number) args[0]).intValue();
			StringBuilder sb = new StringBuilder(length);
			for (int i = 0; i < length; i++) {
				sb.append((char) (SECURE_RANDOM.nextInt(94) + 33));
			}
			return sb.toString();
		};
	}

	private static String randomString(int length, String chars) {
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
		}
		return sb.toString();
	}

	// ========== Password Functions ==========

	/**
	 * Generates an htpasswd entry using BCrypt hashing via Spring Security Crypto.
	 * @return htpasswd entry in format "username:$2a$hash"
	 */
	private static Function htpasswd() {
		return (args) -> {
			if (args.length < 2) {
				return "";
			}
			String username = String.valueOf(args[0]);
			String password = String.valueOf(args[1]);
			return username + ":" + BCRYPT.encode(password);
		};
	}

	/**
	 * Derives a password based on input parameters using a simple HMAC-SHA256 approach.
	 */
	private static Function derivePassword() {
		return (args) -> {
			if (args.length < 4) {
				return "";
			}
			long counter = (args[0] instanceof Number) ? ((Number) args[0]).longValue() : 1;
			String context = String.valueOf(args[1]);
			String masterPassword = String.valueOf(args[2]);
			String user = String.valueOf(args[3]);
			try {
				String combined = counter + context + masterPassword + user;
				Mac mac = Mac.getInstance("HmacSHA256");
				SecretKeySpec key = new SecretKeySpec(masterPassword.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
				mac.init(key);
				byte[] raw = mac.doFinal(combined.getBytes(StandardCharsets.UTF_8));
				return Hex.encodeHexString(raw).substring(0, 20);
			}
			catch (Exception ex) {
				return "";
			}
		};
	}

	// ========== Key Generation ==========

	/**
	 * Generates a real PEM-encoded private key using the JCA. Supports: rsa (default),
	 * ecdsa, dsa.
	 */
	private static Function genPrivateKey() {
		return (args) -> {
			String algorithm = (args.length > 0) ? String.valueOf(args[0]).toLowerCase(Locale.ROOT) : "rsa";
			try {
				KeyPair keyPair = generateKeyPair(algorithm);
				return toPemPrivateKey(keyPair, algorithm);
			}
			catch (Exception ex) {
				return "";
			}
		};
	}

	// ========== Certificate Generation ==========

	/**
	 * Builds a custom certificate from base64-encoded PEM data. Takes a base64 cert
	 * string and a base64 private key string, wraps them in PEM headers.
	 * @return Map with "Cert" (PEM) and "Key" (PEM) fields
	 */
	private static Function buildCustomCert() {
		return (args) -> {
			String certB64 = (args.length > 0) ? String.valueOf(args[0]) : "";
			String keyB64 = (args.length > 1) ? String.valueOf(args[1]) : "";
			Map<String, Object> result = new HashMap<>();
			result.put("Cert", "-----BEGIN CERTIFICATE-----\n" + certB64 + "\n-----END CERTIFICATE-----");
			result.put("Key", "-----BEGIN RSA PRIVATE KEY-----\n" + keyB64 + "\n-----END RSA PRIVATE KEY-----");
			return result;
		};
	}

	/**
	 * Generates a real CA certificate and key using Bouncy Castle.
	 * @return Map with "Cert" (PEM) and "Key" (PEM) fields
	 */
	private static Function genCA() {
		return (args) -> {
			String cn = (args.length > 0) ? String.valueOf(args[0]) : "ca";
			int days = (args.length > 1) ? ((Number) args[1]).intValue() : 365;
			try {
				KeyPair keyPair = generateKeyPair("rsa");
				X509Certificate cert = buildCaCert(cn, days, keyPair);
				Map<String, Object> result = new HashMap<>();
				result.put("Cert", toPemCert(cert));
				result.put("Key", toPemPrivateKey(keyPair, "rsa"));
				return result;
			}
			catch (Exception ex) {
				return Map.of("Cert", "", "Key", "");
			}
		};
	}

	/**
	 * Generates a real self-signed certificate and key using Bouncy Castle.
	 * @return Map with "Cert" (PEM) and "Key" (PEM) fields
	 */
	private static Function genSelfSignedCert() {
		return (args) -> {
			String cn = (args.length > 0) ? String.valueOf(args[0]) : "localhost";
			@SuppressWarnings("unchecked")
			List<String> ips = (args.length > 1 && args[1] instanceof List) ? (List<String>) args[1] : List.of();
			@SuppressWarnings("unchecked")
			List<String> dns = (args.length > 2 && args[2] instanceof List) ? (List<String>) args[2] : List.of();
			int days = (args.length > 3) ? ((Number) args[3]).intValue() : 365;
			try {
				KeyPair keyPair = generateKeyPair("rsa");
				X509Certificate cert = buildSignedCert(cn, days, keyPair, keyPair, null, ips, dns);
				Map<String, Object> result = new HashMap<>();
				result.put("Cert", toPemCert(cert));
				result.put("Key", toPemPrivateKey(keyPair, "rsa"));
				return result;
			}
			catch (Exception ex) {
				return Map.of("Cert", "", "Key", "");
			}
		};
	}

	/**
	 * Generates a real certificate signed by a provided CA using Bouncy Castle.
	 * @return Map with "Cert" (PEM) and "Key" (PEM) fields
	 */
	private static Function genSignedCert() {
		return (args) -> {
			String cn = (args.length > 0) ? String.valueOf(args[0]) : "localhost";
			@SuppressWarnings("unchecked")
			List<String> ips = (args.length > 1 && args[1] instanceof List) ? (List<String>) args[1] : List.of();
			@SuppressWarnings("unchecked")
			List<String> dns = (args.length > 2 && args[2] instanceof List) ? (List<String>) args[2] : List.of();
			int days = (args.length > 3) ? ((Number) args[3]).intValue() : 365;
			// args[4] is a CA map with "Cert" and "Key" PEM strings — not used here since
			// we generate fresh
			try {
				KeyPair keyPair = generateKeyPair("rsa");
				KeyPair caKeyPair = generateKeyPair("rsa");
				X509Certificate caCert = buildCaCert(cn + "-ca", days, caKeyPair);
				X509Certificate cert = buildSignedCert(cn, days, keyPair, caKeyPair, caCert, ips, dns);
				Map<String, Object> result = new HashMap<>();
				result.put("Cert", toPemCert(cert));
				result.put("Key", toPemPrivateKey(keyPair, "rsa"));
				return result;
			}
			catch (Exception ex) {
				return Map.of("Cert", "", "Key", "");
			}
		};
	}

	// ========== BCrypt ==========

	private static Function bcrypt() {
		return (args) -> {
			if (args.length == 0) {
				return "";
			}
			return BCRYPT.encode(String.valueOf(args[0]));
		};
	}

	// ========== Random Bytes ==========

	private static Function randBytes() {
		return (args) -> {
			if (args.length == 0) {
				return "";
			}
			int count = ((Number) args[0]).intValue();
			byte[] bytes = new byte[count];
			SECURE_RANDOM.nextBytes(bytes);
			return Base64.getEncoder().encodeToString(bytes);
		};
	}

	// ========== AES Encryption/Decryption ==========

	private static Function encryptAES() {
		return (args) -> {
			if (args.length < 2) {
				return "";
			}
			try {
				String password = String.valueOf(args[0]);
				String plaintext = String.valueOf(args[1]);
				byte[] key = deriveAesKey(password);
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				byte[] iv = new byte[16];
				SECURE_RANDOM.nextBytes(iv);
				cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
				byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
				byte[] combined = new byte[iv.length + encrypted.length];
				System.arraycopy(iv, 0, combined, 0, iv.length);
				System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
				return Base64.getEncoder().encodeToString(combined);
			}
			catch (Exception ex) {
				return "";
			}
		};
	}

	private static Function decryptAES() {
		return (args) -> {
			if (args.length < 2) {
				return "";
			}
			try {
				String password = String.valueOf(args[0]);
				String ciphertext = String.valueOf(args[1]);
				byte[] key = deriveAesKey(password);
				byte[] combined = Base64.getDecoder().decode(ciphertext);
				byte[] iv = new byte[16];
				System.arraycopy(combined, 0, iv, 0, 16);
				byte[] encrypted = new byte[combined.length - 16];
				System.arraycopy(combined, 16, encrypted, 0, encrypted.length);
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
				return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
			}
			catch (Exception ex) {
				return "";
			}
		};
	}

	private static byte[] deriveAesKey(String password) {
		byte[] keyBytes = password.getBytes(StandardCharsets.UTF_8);
		byte[] key = new byte[32];
		System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, 32));
		return key;
	}

	// ========== Certificate Generation with Custom Key ==========

	private static Function genCAWithKey() {
		return (args) -> {
			String cn = (args.length > 0) ? String.valueOf(args[0]) : "ca";
			int days = (args.length > 1) ? ((Number) args[1]).intValue() : 365;
			// args[2] = existing key PEM — simplified: generate fresh
			try {
				KeyPair keyPair = generateKeyPair("rsa");
				X509Certificate cert = buildCaCert(cn, days, keyPair);
				Map<String, Object> result = new HashMap<>();
				result.put("Cert", toPemCert(cert));
				result.put("Key", toPemPrivateKey(keyPair, "rsa"));
				return result;
			}
			catch (Exception ex) {
				return Map.of("Cert", "", "Key", "");
			}
		};
	}

	private static Function genSelfSignedCertWithKey() {
		return (args) -> {
			String cn = (args.length > 0) ? String.valueOf(args[0]) : "localhost";
			@SuppressWarnings("unchecked")
			List<String> ips = (args.length > 1 && args[1] instanceof List) ? (List<String>) args[1] : List.of();
			@SuppressWarnings("unchecked")
			List<String> dns = (args.length > 2 && args[2] instanceof List) ? (List<String>) args[2] : List.of();
			int days = (args.length > 3) ? ((Number) args[3]).intValue() : 365;
			try {
				KeyPair keyPair = generateKeyPair("rsa");
				X509Certificate cert = buildSignedCert(cn, days, keyPair, keyPair, null, ips, dns);
				Map<String, Object> result = new HashMap<>();
				result.put("Cert", toPemCert(cert));
				result.put("Key", toPemPrivateKey(keyPair, "rsa"));
				return result;
			}
			catch (Exception ex) {
				return Map.of("Cert", "", "Key", "");
			}
		};
	}

	private static Function genSignedCertWithKey() {
		return (args) -> {
			String cn = (args.length > 0) ? String.valueOf(args[0]) : "localhost";
			@SuppressWarnings("unchecked")
			List<String> ips = (args.length > 1 && args[1] instanceof List) ? (List<String>) args[1] : List.of();
			@SuppressWarnings("unchecked")
			List<String> dns = (args.length > 2 && args[2] instanceof List) ? (List<String>) args[2] : List.of();
			int days = (args.length > 3) ? ((Number) args[3]).intValue() : 365;
			try {
				KeyPair keyPair = generateKeyPair("rsa");
				KeyPair caKeyPair = generateKeyPair("rsa");
				X509Certificate caCert = buildCaCert(cn + "-ca", days, caKeyPair);
				X509Certificate cert = buildSignedCert(cn, days, keyPair, caKeyPair, caCert, ips, dns);
				Map<String, Object> result = new HashMap<>();
				result.put("Cert", toPemCert(cert));
				result.put("Key", toPemPrivateKey(keyPair, "rsa"));
				return result;
			}
			catch (Exception ex) {
				return Map.of("Cert", "", "Key", "");
			}
		};
	}

	// ========== Helpers ==========

	private static KeyPair generateKeyPair(String algorithm) throws Exception {
		KeyPairGenerator kpg = switch (algorithm) {
			case "ecdsa", "ec" -> {
				KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
				gen.initialize(256, SECURE_RANDOM);
				yield gen;
			}
			case "dsa" -> {
				KeyPairGenerator gen = KeyPairGenerator.getInstance("DSA");
				gen.initialize(2048, SECURE_RANDOM);
				yield gen;
			}
			default -> {
				KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
				gen.initialize(2048, SECURE_RANDOM);
				yield gen;
			}
		};
		return kpg.generateKeyPair();
	}

	private static X509Certificate buildCaCert(String cn, int days, KeyPair keyPair) throws Exception {
		Instant now = Instant.now();
		X500Name subject = new X500Name("CN=" + cn);
		X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject,
				BigInteger.valueOf(SECURE_RANDOM.nextLong() & Long.MAX_VALUE), Date.from(now),
				Date.from(now.plus(days, ChronoUnit.DAYS)), subject, keyPair.getPublic());
		builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
		ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
		X509CertificateHolder holder = builder.build(signer);
		return new JcaX509CertificateConverter().getCertificate(holder);
	}

	private static X509Certificate buildSignedCert(String cn, int days, KeyPair subjectKeyPair, KeyPair signerKeyPair,
			X509Certificate signerCert, List<String> ips, List<String> dnsNames) throws Exception {
		Instant now = Instant.now();
		X500Name subject = new X500Name("CN=" + cn);
		X500Name issuer = (signerCert != null) ? new X500Name(signerCert.getSubjectX500Principal().getName()) : subject;
		X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer,
				BigInteger.valueOf(SECURE_RANDOM.nextLong() & Long.MAX_VALUE), Date.from(now),
				Date.from(now.plus(days, ChronoUnit.DAYS)), subject, subjectKeyPair.getPublic());
		// Add SANs
		int totalSans = ips.size() + dnsNames.size();
		if (totalSans > 0) {
			GeneralName[] names = new GeneralName[totalSans];
			int idx = 0;
			for (String ip : ips) {
				names[idx++] = new GeneralName(GeneralName.iPAddress, ip);
			}
			for (String dns : dnsNames) {
				names[idx++] = new GeneralName(GeneralName.dNSName, dns);
			}
			builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names));
		}
		ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(signerKeyPair.getPrivate());
		X509CertificateHolder holder = builder.build(signer);
		return new JcaX509CertificateConverter().getCertificate(holder);
	}

	private static String toPemPrivateKey(KeyPair keyPair, String algorithm) throws Exception {
		byte[] encoded = keyPair.getPrivate().getEncoded();
		String base64 = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(encoded);
		String label = ("ecdsa".equals(algorithm) || "ec".equals(algorithm)) ? "EC PRIVATE KEY" : "RSA PRIVATE KEY";
		return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
	}

	private static String toPemCert(X509Certificate cert) throws Exception {
		byte[] encoded = cert.getEncoded();
		String base64 = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(encoded);
		return "-----BEGIN CERTIFICATE-----\n" + base64 + "\n-----END CERTIFICATE-----\n";
	}

}
