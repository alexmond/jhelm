package org.alexmond.jhelm.core.action;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.JhelmException;
import org.alexmond.jhelm.core.service.SignatureService;
import org.alexmond.jhelm.core.service.VerificationKeyring;

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
	 * @throws JhelmException if the provenance file cannot be read
	 * @throws SignatureException if verification fails
	 */
	public void verify(String chartTgzPath, String keyringPath) {
		File chartFile = new File(chartTgzPath);
		if (!chartFile.exists()) {
			throw new IllegalArgumentException("Chart archive not found: " + chartTgzPath);
		}

		File provFile = new File(chartTgzPath + ".prov");
		if (!provFile.exists()) {
			throw new IllegalArgumentException("Provenance file not found: " + provFile.getAbsolutePath());
		}

		String provContent;
		try {
			provContent = Files.readString(provFile.toPath());
		}
		catch (IOException ex) {
			throw new JhelmException("Failed to read provenance file " + provFile.getAbsolutePath(), ex);
		}
		VerificationKeyring keyring = signatureService.loadVerificationKeyring(keyringPath);

		signatureService.verify(chartFile, provContent, keyring);
		if (log.isInfoEnabled()) {
			log.info("Verification succeeded for {}", chartFile.getName());
		}
	}

}
