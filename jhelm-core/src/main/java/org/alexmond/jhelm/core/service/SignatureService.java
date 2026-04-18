package org.alexmond.jhelm.core.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Iterator;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.alexmond.jhelm.core.model.ChartMetadata;

/**
 * PGP signing and verification service for Helm chart provenance files. Generates and
 * verifies {@code .prov} files compatible with the Helm provenance format.
 *
 * @see <a href="https://helm.sh/docs/topics/provenance/">Helm Provenance and
 * Integrity</a>
 */
@Slf4j
public class SignatureService {

	static {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	/**
	 * Signs a chart archive and produces a Helm-compatible provenance file content.
	 * @param chartTgz the chart archive file
	 * @param metadata the chart metadata
	 * @param secretKey the PGP secret key to sign with
	 * @param passphrase the key passphrase
	 * @return the provenance file content (PGP clear-signed YAML)
	 * @throws IOException if the chart file cannot be read
	 * @throws PGPException if signing fails
	 */
	public String sign(File chartTgz, ChartMetadata metadata, PGPSecretKey secretKey, char[] passphrase)
			throws IOException, PGPException {
		String digest = computeSha256(chartTgz);
		String yamlContent = buildProvenanceYaml(metadata, chartTgz.getName(), digest);

		PGPPrivateKey privateKey = secretKey
			.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(passphrase));

		PGPSignatureGenerator sigGen = new PGPSignatureGenerator(
				new JcaPGPContentSignerBuilder(secretKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256)
					.setProvider("BC"),
				secretKey.getPublicKey());
		sigGen.init(PGPSignature.CANONICAL_TEXT_DOCUMENT, privateKey);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ArmoredOutputStream armoredOut = new ArmoredOutputStream(out)) {
			armoredOut.beginClearText(HashAlgorithmTags.SHA256);

			byte[] yamlBytes = yamlContent.getBytes(StandardCharsets.UTF_8);
			armoredOut.write(yamlBytes);
			sigGen.update(yamlBytes);

			armoredOut.endClearText();

			try (BCPGOutputStream bcpgOut = new BCPGOutputStream(armoredOut)) {
				sigGen.generate().encode(bcpgOut);
			}
		}

		return out.toString(StandardCharsets.UTF_8);
	}

	/**
	 * Verifies a chart archive against its provenance file.
	 * @param chartTgz the chart archive file
	 * @param provContent the provenance file content
	 * @param publicKeys the public keyring collection
	 * @throws IOException if files cannot be read
	 * @throws PGPException if verification fails
	 * @throws SignatureVerificationException if the signature is invalid or the digest
	 * does not match
	 */
	public void verify(File chartTgz, String provContent, PGPPublicKeyRingCollection publicKeys)
			throws IOException, PGPException {
		// Extract the signed data and signature from the clear-signed message
		String signedData = extractSignedData(provContent);
		byte[] signatureBytes = extractSignatureBytes(provContent);

		// Verify the PGP signature
		PGPObjectFactory pgpFactory = new PGPObjectFactory(signatureBytes, new JcaKeyFingerprintCalculator());
		PGPSignatureList sigList = (PGPSignatureList) pgpFactory.nextObject();
		if (sigList == null || sigList.isEmpty()) {
			throw new SignatureVerificationException("No PGP signature found in provenance file");
		}
		PGPSignature signature = sigList.get(0);

		PGPPublicKey verifyKey = publicKeys.getPublicKey(signature.getKeyID());
		if (verifyKey == null) {
			throw new SignatureVerificationException(
					"No public key found for key ID " + Long.toHexString(signature.getKeyID()));
		}

		signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), verifyKey);
		byte[] signedBytes = signedData.getBytes(StandardCharsets.UTF_8);
		signature.update(signedBytes);

		if (!signature.verify()) {
			throw new SignatureVerificationException("PGP signature verification failed");
		}
		log.info("PGP signature verified successfully");

		// Verify the chart digest
		String expectedDigest = extractDigest(signedData);
		if (expectedDigest == null) {
			throw new SignatureVerificationException("No digest found in provenance YAML");
		}

		String actualDigest = "sha256:" + computeSha256(chartTgz);
		if (!expectedDigest.equals(actualDigest)) {
			throw new SignatureVerificationException(
					"Digest mismatch: expected " + expectedDigest + ", got " + actualDigest);
		}
		log.info("Chart digest verified: {}", actualDigest);
	}

	/**
	 * Loads a PGP secret key from a keyring file by matching a substring of the key's
	 * user ID.
	 * @param keyringPath path to the secret keyring file
	 * @param keyId substring to match against key user IDs
	 * @return the matching secret key
	 * @throws IOException if the keyring cannot be read
	 * @throws PGPException if the keyring is invalid
	 */
	public PGPSecretKey loadSecretKey(String keyringPath, String keyId) throws IOException, PGPException {
		try (InputStream in = PGPUtil.getDecoderStream(new FileInputStream(keyringPath))) {
			PGPSecretKeyRingCollection keyRings = new PGPSecretKeyRingCollection(in, new JcaKeyFingerprintCalculator());

			for (PGPSecretKeyRing ring : keyRings) {
				for (PGPSecretKey key : ring) {
					if (!key.isSigningKey()) {
						continue;
					}
					Iterator<String> userIds = key.getUserIDs();
					while (userIds.hasNext()) {
						if (userIds.next().contains(keyId)) {
							return key;
						}
					}
				}
			}
		}
		throw new PGPException("No signing key found matching: " + keyId);
	}

	/**
	 * Loads a PGP public keyring collection from a file.
	 * @param keyringPath path to the public keyring file
	 * @return the public keyring collection
	 * @throws IOException if the keyring cannot be read
	 * @throws PGPException if the keyring is invalid
	 */
	public PGPPublicKeyRingCollection loadPublicKeyring(String keyringPath) throws IOException, PGPException {
		try (InputStream in = PGPUtil.getDecoderStream(new FileInputStream(keyringPath))) {
			return new PGPPublicKeyRingCollection(in, new JcaKeyFingerprintCalculator());
		}
	}

	/**
	 * Builds the YAML content for a provenance file.
	 */
	String buildProvenanceYaml(ChartMetadata metadata, String archiveName, String digest) {
		StringBuilder sb = new StringBuilder();
		sb.append("apiVersion: ").append((metadata.getApiVersion() != null) ? metadata.getApiVersion() : "v2");
		sb.append('\n');
		if (metadata.getAppVersion() != null) {
			sb.append("appVersion: ").append(metadata.getAppVersion()).append('\n');
		}
		if (metadata.getDescription() != null) {
			sb.append("description: ").append(metadata.getDescription()).append('\n');
		}
		sb.append("name: ").append(metadata.getName()).append('\n');
		if (metadata.getType() != null) {
			sb.append("type: ").append(metadata.getType()).append('\n');
		}
		sb.append("version: ")
			.append(metadata.getVersion())
			.append("\n...\nfiles:\n  ")
			.append(archiveName)
			.append(": sha256:")
			.append(digest)
			.append('\n');
		return sb.toString();
	}

	private String computeSha256(File file) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (InputStream is = new FileInputStream(file)) {
				byte[] buf = new byte[8192];
				int read;
				while ((read = is.read(buf)) != -1) {
					md.update(buf, 0, read);
				}
			}
			byte[] hash = md.digest();
			StringBuilder hex = new StringBuilder();
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IOException("SHA-256 algorithm not available", ex);
		}
	}

	/**
	 * Extracts the signed text body from a PGP clear-signed message.
	 */
	String extractSignedData(String clearSigned) {
		int bodyStart = clearSigned.indexOf("Hash:");
		if (bodyStart < 0) {
			throw new SignatureVerificationException("Invalid clear-signed message: no Hash header");
		}
		// Skip to the blank line after Hash header
		int blankLine = clearSigned.indexOf("\n\n", bodyStart);
		if (blankLine < 0) {
			throw new SignatureVerificationException("Invalid clear-signed message: no blank line after Hash header");
		}

		int sigStart = clearSigned.indexOf("-----BEGIN PGP SIGNATURE-----");
		if (sigStart < 0) {
			throw new SignatureVerificationException("Invalid clear-signed message: no signature block");
		}
		return clearSigned.substring(blankLine + 2, sigStart);
	}

	/**
	 * Extracts the raw PGP signature bytes from a clear-signed message.
	 */
	private byte[] extractSignatureBytes(String clearSigned) throws IOException {
		int sigStart = clearSigned.indexOf("-----BEGIN PGP SIGNATURE-----");
		if (sigStart < 0) {
			throw new SignatureVerificationException("No PGP signature block found");
		}
		String sigBlock = clearSigned.substring(sigStart);
		return PGPUtil.getDecoderStream(new ByteArrayInputStream(sigBlock.getBytes(StandardCharsets.UTF_8)))
			.readAllBytes();
	}

	/**
	 * Extracts the digest value from the provenance YAML's files section.
	 */
	private String extractDigest(String yamlContent) {
		// Look for pattern: filename: sha256:hexdigest
		for (String line : yamlContent.split("\n")) {
			String trimmed = line.trim();
			if (trimmed.contains(": sha256:")) {
				int idx = trimmed.indexOf(": sha256:");
				return trimmed.substring(idx + 2);
			}
		}
		return null;
	}

	/**
	 * Exception thrown when signature verification fails.
	 */
	public static class SignatureVerificationException extends RuntimeException {

		public SignatureVerificationException(String message) {
			super(message);
		}

	}

}
