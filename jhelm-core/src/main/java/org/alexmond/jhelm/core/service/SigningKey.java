package org.alexmond.jhelm.core.service;

import org.bouncycastle.openpgp.PGPSecretKey;

/**
 * Opaque handle to a PGP secret key used for signing chart provenance files. Wrapping the
 * underlying BouncyCastle {@code PGPSecretKey} keeps the OpenPGP implementation off the
 * public API surface, so consumers can hold and pass a signing key around without a
 * compile-time dependency on BouncyCastle.
 *
 * <p>
 * Obtain instances from {@link SignatureService#loadSigningKey(String, String)}. Only
 * {@link SignatureService}, which lives in this package, can unwrap the contained key.
 */
public final class SigningKey {

	private final PGPSecretKey secretKey;

	/**
	 * Wraps the given secret key. Package-private so only {@link SignatureService} can
	 * construct instances.
	 * @param secretKey the underlying PGP secret key
	 */
	SigningKey(PGPSecretKey secretKey) {
		this.secretKey = secretKey;
	}

	/**
	 * Returns the wrapped PGP secret key. Package-private so the BouncyCastle type stays
	 * internal to this package.
	 * @return the underlying PGP secret key
	 */
	PGPSecretKey pgpSecretKey() {
		return this.secretKey;
	}

}
