package org.alexmond.jhelm.core.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Date;
import java.util.List;

import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.service.SignatureService.SignatureVerificationException;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureServiceTest {

	private static final char[] PASSPHRASE = "testpass".toCharArray();

	private static final String USER_ID = "Test User <test@example.com>";

	private static PGPSecretKey secretKey;

	private static PGPPublicKeyRingCollection publicKeys;

	private SignatureService signatureService;

	@TempDir
	Path tempDir;

	@BeforeAll
	static void generateKeys() throws Exception {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}

		PGPKeyRingGenerator keyRingGen = createKeyRingGenerator(USER_ID);
		secretKey = keyRingGen.generateSecretKeyRing().getSecretKey();
		publicKeys = new PGPPublicKeyRingCollection(List.of(keyRingGen.generatePublicKeyRing()));
	}

	@BeforeEach
	void setUp() {
		signatureService = new SignatureService();
	}

	@Test
	void testSignProducesValidClearSignedMessage() throws Exception {
		File chartFile = createTempChartFile("test-chart-0.1.0.tgz", "chart-content");
		ChartMetadata metadata = ChartMetadata.builder().name("test-chart").version("0.1.0").apiVersion("v2").build();

		String provContent = signatureService.sign(chartFile, metadata, secretKey, PASSPHRASE);

		assertNotNull(provContent);
		assertTrue(provContent.contains("-----BEGIN PGP SIGNED MESSAGE-----"));
		assertTrue(provContent.contains("Hash: SHA256"));
		assertTrue(provContent.contains("-----BEGIN PGP SIGNATURE-----"));
		assertTrue(provContent.contains("-----END PGP SIGNATURE-----"));
		assertTrue(provContent.contains("name: test-chart"));
		assertTrue(provContent.contains("version: 0.1.0"));
	}

	@Test
	void testSignAndVerifyRoundTrip() throws Exception {
		File chartFile = createTempChartFile("mychart-1.0.0.tgz", "binary-chart-content-here");
		ChartMetadata metadata = ChartMetadata.builder()
			.name("mychart")
			.version("1.0.0")
			.apiVersion("v2")
			.description("A test chart")
			.appVersion("2.0.0")
			.type("application")
			.build();

		String provContent = signatureService.sign(chartFile, metadata, secretKey, PASSPHRASE);

		assertDoesNotThrow(() -> signatureService.verify(chartFile, provContent, publicKeys));
	}

	@Test
	void testVerifyFailsWithTamperedChart() throws Exception {
		File chartFile = createTempChartFile("tampered-1.0.0.tgz", "original-content");
		ChartMetadata metadata = ChartMetadata.builder().name("tampered").version("1.0.0").apiVersion("v2").build();

		String provContent = signatureService.sign(chartFile, metadata, secretKey, PASSPHRASE);

		// Overwrite the chart file with different content
		try (OutputStream os = new FileOutputStream(chartFile)) {
			os.write("tampered-content".getBytes());
		}

		SignatureVerificationException ex = assertThrows(SignatureVerificationException.class,
				() -> signatureService.verify(chartFile, provContent, publicKeys));
		assertTrue(ex.getMessage().contains("Digest mismatch"));
	}

	@Test
	void testVerifyFailsWithWrongPublicKey() throws Exception {
		PGPKeyRingGenerator otherGen = createKeyRingGenerator("Other <other@example.com>");
		PGPPublicKeyRingCollection wrongKeys = new PGPPublicKeyRingCollection(
				List.of(otherGen.generatePublicKeyRing()));

		File chartFile = createTempChartFile("wrongkey-1.0.0.tgz", "content");
		ChartMetadata metadata = ChartMetadata.builder().name("wrongkey").version("1.0.0").apiVersion("v2").build();

		String provContent = signatureService.sign(chartFile, metadata, secretKey, PASSPHRASE);

		SignatureVerificationException ex = assertThrows(SignatureVerificationException.class,
				() -> signatureService.verify(chartFile, provContent, wrongKeys));
		assertTrue(ex.getMessage().contains("No public key found"));
	}

	@Test
	void testBuildProvenanceYamlIncludesAllFields() {
		ChartMetadata metadata = ChartMetadata.builder()
			.name("full-chart")
			.version("2.1.0")
			.apiVersion("v2")
			.appVersion("3.0.0")
			.description("A full chart")
			.type("application")
			.build();

		String yaml = signatureService.buildProvenanceYaml(metadata, "full-chart-2.1.0.tgz", "abc123");

		assertTrue(yaml.contains("apiVersion: v2"));
		assertTrue(yaml.contains("appVersion: 3.0.0"));
		assertTrue(yaml.contains("description: A full chart"));
		assertTrue(yaml.contains("name: full-chart"));
		assertTrue(yaml.contains("type: application"));
		assertTrue(yaml.contains("version: 2.1.0"));
		assertTrue(yaml.contains("full-chart-2.1.0.tgz: sha256:abc123"));
	}

	@Test
	void testBuildProvenanceYamlOmitsNullFields() {
		ChartMetadata metadata = ChartMetadata.builder().name("minimal").version("1.0.0").build();

		String yaml = signatureService.buildProvenanceYaml(metadata, "minimal-1.0.0.tgz", "def456");

		assertTrue(yaml.contains("apiVersion: v2"));
		assertTrue(yaml.contains("name: minimal"));
		assertTrue(yaml.contains("version: 1.0.0"));
		assertTrue(!yaml.contains("appVersion:"));
		assertTrue(!yaml.contains("description:"));
		assertTrue(!yaml.contains("type:"));
	}

	@Test
	void testExtractSignedDataValid() throws Exception {
		File chartFile = createTempChartFile("extract-1.0.0.tgz", "data");
		ChartMetadata metadata = ChartMetadata.builder().name("extract").version("1.0.0").apiVersion("v2").build();

		String provContent = signatureService.sign(chartFile, metadata, secretKey, PASSPHRASE);
		String signedData = signatureService.extractSignedData(provContent);

		assertNotNull(signedData);
		assertTrue(signedData.contains("name: extract"));
		assertTrue(signedData.contains("version: 1.0.0"));
	}

	@Test
	void testExtractSignedDataInvalidNoHash() {
		SignatureVerificationException ex = assertThrows(SignatureVerificationException.class,
				() -> signatureService.extractSignedData("no hash here"));
		assertTrue(ex.getMessage().contains("no Hash header"));
	}

	@Test
	void testExtractSignedDataInvalidNoBlankLine() {
		SignatureVerificationException ex = assertThrows(SignatureVerificationException.class,
				() -> signatureService.extractSignedData("Hash: SHA256\nno blank line"));
		assertTrue(ex.getMessage().contains("no blank line"));
	}

	@Test
	void testExtractSignedDataInvalidNoSignatureBlock() {
		SignatureVerificationException ex = assertThrows(SignatureVerificationException.class,
				() -> signatureService.extractSignedData("Hash: SHA256\n\ndata but no sig"));
		assertTrue(ex.getMessage().contains("no signature block"));
	}

	@Test
	void testLoadSecretKeyFromFile() throws Exception {
		// Write the secret key to a file
		Path keyFile = tempDir.resolve("secret.gpg");
		PGPSecretKeyRingCollection collection = new PGPSecretKeyRingCollection(
				List.of(new PGPSecretKeyRing(secretKey.getEncoded(), new JcaKeyFingerprintCalculator())));
		try (OutputStream out = new FileOutputStream(keyFile.toFile());
				ArmoredOutputStream armoredOut = new ArmoredOutputStream(out)) {
			collection.encode(armoredOut);
		}

		PGPSecretKey loaded = signatureService.loadSecretKey(keyFile.toString(), "test@example.com");
		assertNotNull(loaded);
		assertEquals(secretKey.getKeyID(), loaded.getKeyID());
	}

	@Test
	void testLoadSecretKeyNotFound() throws Exception {
		Path keyFile = tempDir.resolve("secret2.gpg");
		PGPSecretKeyRingCollection collection = new PGPSecretKeyRingCollection(
				List.of(new PGPSecretKeyRing(secretKey.getEncoded(), new JcaKeyFingerprintCalculator())));
		try (OutputStream out = new FileOutputStream(keyFile.toFile());
				ArmoredOutputStream armoredOut = new ArmoredOutputStream(out)) {
			collection.encode(armoredOut);
		}

		assertThrows(PGPException.class,
				() -> signatureService.loadSecretKey(keyFile.toString(), "nonexistent@example.com"));
	}

	@Test
	void testLoadPublicKeyringFromFile() throws Exception {
		Path keyFile = tempDir.resolve("public.gpg");
		try (OutputStream out = new FileOutputStream(keyFile.toFile());
				ArmoredOutputStream armoredOut = new ArmoredOutputStream(out)) {
			publicKeys.encode(armoredOut);
		}

		PGPPublicKeyRingCollection loaded = signatureService.loadPublicKeyring(keyFile.toString());
		assertNotNull(loaded);
		assertTrue(loaded.contains(secretKey.getKeyID()));
	}

	@Test
	void testVerifyRejectsNoSignatureContent() {
		File chartFile = tempDir.resolve("nosig.tgz").toFile();
		assertThrows(SignatureVerificationException.class,
				() -> signatureService.verify(chartFile, "no signature content", publicKeys));
	}

	@SuppressWarnings("deprecation") // JcaPGPKeyPair 3-arg constructor; 4-arg version has
										// a BouncyCastle bug
	private static PGPKeyRingGenerator createKeyRingGenerator(String userId) throws Exception {
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
		kpg.initialize(2048);
		KeyPair keyPair = kpg.generateKeyPair();
		PGPKeyPair pgpKeyPair = new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, keyPair, new Date());
		return new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, pgpKeyPair, userId,
				new JcaPGPDigestCalculatorProviderBuilder().setProvider("BC").build().get(HashAlgorithmTags.SHA1), null,
				null,
				new JcaPGPContentSignerBuilder(pgpKeyPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256)
					.setProvider("BC"),
				new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256,
						new JcaPGPDigestCalculatorProviderBuilder().setProvider("BC")
							.build()
							.get(HashAlgorithmTags.SHA256))
					.setProvider("BC")
					.build(PASSPHRASE));
	}

	private File createTempChartFile(String name, String content) throws IOException {
		File file = tempDir.resolve(name).toFile();
		try (OutputStream os = new FileOutputStream(file)) {
			os.write(content.getBytes());
		}
		return file;
	}

}
