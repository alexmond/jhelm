package org.alexmond.jhelm.core.action;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Date;
import java.util.List;

import org.alexmond.jhelm.core.service.SignatureService;
import org.alexmond.jhelm.core.service.SignatureService.SignatureVerificationException;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.alexmond.jhelm.core.model.ChartMetadata;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyActionTest {

	private static final char[] PASSPHRASE = "testpass".toCharArray();

	private static PGPSecretKey secretKey;

	private static PGPPublicKeyRingCollection publicKeys;

	private SignatureService signatureService;

	private VerifyAction verifyAction;

	@TempDir
	Path tempDir;

	@BeforeAll
	@SuppressWarnings("deprecation") // JcaPGPKeyPair 3-arg constructor; 4-arg version has
										// a BouncyCastle bug
	static void generateKeys() throws Exception {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
		kpg.initialize(2048);
		KeyPair keyPair = kpg.generateKeyPair();
		var pgpKeyPair = new JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, keyPair, new Date());
		PGPKeyRingGenerator gen = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, pgpKeyPair,
				"Test <test@example.com>",
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
		secretKey = gen.generateSecretKeyRing().getSecretKey();
		publicKeys = new PGPPublicKeyRingCollection(List.of(gen.generatePublicKeyRing()));
	}

	@BeforeEach
	void setUp() {
		signatureService = new SignatureService();
		verifyAction = new VerifyAction(signatureService);
	}

	@Test
	void testVerifyValidSignature() throws Exception {
		File chartFile = createSignedChart("valid-1.0.0.tgz", "chart-data");
		Path keyringFile = writePublicKeyring();

		assertDoesNotThrow(() -> verifyAction.verify(chartFile.getAbsolutePath(), keyringFile.toString()));
	}

	@Test
	void testVerifyFailsWhenChartTampered() throws Exception {
		File chartFile = createSignedChart("tampered-1.0.0.tgz", "original-data");
		Path keyringFile = writePublicKeyring();

		// Tamper with the chart
		try (OutputStream os = new FileOutputStream(chartFile)) {
			os.write("tampered-data".getBytes());
		}

		assertThrows(SignatureVerificationException.class,
				() -> verifyAction.verify(chartFile.getAbsolutePath(), keyringFile.toString()));
	}

	@Test
	void testVerifyFailsWhenChartNotFound() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> verifyAction.verify("/nonexistent/chart.tgz", "/some/keyring"));
		assertTrue(ex.getMessage().contains("Chart archive not found"));
	}

	@Test
	void testVerifyFailsWhenProvFileNotFound() throws Exception {
		File chartFile = tempDir.resolve("noprov-1.0.0.tgz").toFile();
		try (OutputStream os = new FileOutputStream(chartFile)) {
			os.write("data".getBytes());
		}

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> verifyAction.verify(chartFile.getAbsolutePath(), "/some/keyring"));
		assertTrue(ex.getMessage().contains("Provenance file not found"));
	}

	private File createSignedChart(String name, String content) throws Exception {
		File chartFile = tempDir.resolve(name).toFile();
		try (OutputStream os = new FileOutputStream(chartFile)) {
			os.write(content.getBytes());
		}

		ChartMetadata metadata = ChartMetadata.builder()
			.name(name.replace(".tgz", ""))
			.version("1.0.0")
			.apiVersion("v2")
			.build();

		String provContent = signatureService.sign(chartFile, metadata, secretKey, PASSPHRASE);
		Files.writeString(tempDir.resolve(name + ".prov"), provContent);

		return chartFile;
	}

	private Path writePublicKeyring() throws Exception {
		Path keyringFile = tempDir.resolve("pubring.gpg");
		try (OutputStream out = new FileOutputStream(keyringFile.toFile());
				ArmoredOutputStream armoredOut = new ArmoredOutputStream(out)) {
			publicKeys.encode(armoredOut);
		}
		return keyringFile;
	}

}
