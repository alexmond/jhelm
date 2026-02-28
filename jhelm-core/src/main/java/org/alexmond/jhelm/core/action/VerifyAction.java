package org.alexmond.jhelm.core.action;

import java.io.File;
import java.nio.file.Files;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.alexmond.jhelm.core.service.SignatureService;

/**
 * Verifies a packaged chart archive against its PGP provenance file.
 */
@Slf4j
@RequiredArgsConstructor
public class VerifyAction {

	private final SignatureService signatureService;

	/**
	 * Verifies the PGP signature and digest of a chart archive.
	 * @param chartTgzPath path to the chart {@code .tgz} archive
	 * @param keyringPath path to the PGP public keyring file
	 */
	public void verify(String chartTgzPath, String keyringPath) throws Exception {
		File chartFile = new File(chartTgzPath);
		if (!chartFile.exists()) {
			throw new IllegalArgumentException("Chart archive not found: " + chartTgzPath);
		}

		File provFile = new File(chartTgzPath + ".prov");
		if (!provFile.exists()) {
			throw new IllegalArgumentException("Provenance file not found: " + provFile.getAbsolutePath());
		}

		String provContent = Files.readString(provFile.toPath());
		PGPPublicKeyRingCollection publicKeys = signatureService.loadPublicKeyring(keyringPath);

		signatureService.verify(chartFile, provContent, publicKeys);
		if (log.isInfoEnabled()) {
			log.info("Verification succeeded for {}", chartFile.getName());
		}
	}

}
