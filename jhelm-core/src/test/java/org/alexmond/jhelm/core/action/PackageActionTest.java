package org.alexmond.jhelm.core.action;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Date;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.SignatureService;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageActionTest {

	private static final char[] PASSPHRASE = "testpass".toCharArray();

	private static PGPSecretKey secretKey;

	private PackageAction packageAction;

	@TempDir
	Path tempDir;

	@BeforeAll
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
	}

	@BeforeEach
	void setUp() {
		packageAction = new PackageAction(new ChartLoader(), new SignatureService());
	}

	@Test
	void testPackageChartCreatesArchive() throws Exception {
		Path chartDir = createMinimalChart("test-chart", "1.0.0");
		packageAction.setDestination(tempDir.toFile());

		File archive = packageAction.packageChart(chartDir.toString());

		assertNotNull(archive);
		assertTrue(archive.exists());
		assertTrue(archive.getName().equals("test-chart-1.0.0.tgz"));
		assertTrue(archive.length() > 0);
	}

	@Test
	void testPackageChartWithSignatureCreatesProvFile() throws Exception {
		Path chartDir = createMinimalChart("signed-chart", "2.0.0");
		packageAction.setDestination(tempDir.toFile());

		File archive = packageAction.packageChart(chartDir.toString(), secretKey, PASSPHRASE);

		assertTrue(archive.exists());
		File provFile = new File(archive.getAbsolutePath() + ".prov");
		assertTrue(provFile.exists());
		String provContent = Files.readString(provFile.toPath());
		assertTrue(provContent.contains("-----BEGIN PGP SIGNED MESSAGE-----"));
		assertTrue(provContent.contains("name: signed-chart"));
	}

	@Test
	void testPackageChartWithoutSignatureNoProvFile() throws Exception {
		Path chartDir = createMinimalChart("unsigned-chart", "1.0.0");
		packageAction.setDestination(tempDir.toFile());

		File archive = packageAction.packageChart(chartDir.toString());

		assertTrue(archive.exists());
		File provFile = new File(archive.getAbsolutePath() + ".prov");
		assertTrue(!provFile.exists());
	}

	private Path createMinimalChart(String name, String version) throws Exception {
		Path chartDir = tempDir.resolve(name);
		Files.createDirectories(chartDir.resolve("templates"));
		Files.writeString(chartDir.resolve("Chart.yaml"), """
				apiVersion: v2
				name: %s
				version: %s
				description: A test chart
				""".formatted(name, version));
		Files.writeString(chartDir.resolve("values.yaml"), "replicaCount: 1\n");
		Files.writeString(chartDir.resolve("templates/deployment.yaml"), """
				apiVersion: apps/v1
				kind: Deployment
				metadata:
				  name: {{ .Release.Name }}
				""");
		return chartDir;
	}

}
