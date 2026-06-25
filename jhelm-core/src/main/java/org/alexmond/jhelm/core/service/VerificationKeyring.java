package org.alexmond.jhelm.core.service;

import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;

/**
 * Opaque handle to a collection of PGP public keys used to verify chart provenance files.
 * Wrapping the underlying BouncyCastle {@code PGPPublicKeyRingCollection} keeps the
 * OpenPGP implementation off the public API surface, so consumers can hold and pass a
 * keyring around without a compile-time dependency on BouncyCastle.
 *
 * <p>
 * Obtain instances from {@link SignatureService#loadVerificationKeyring(String)}. Only
 * {@link SignatureService}, which lives in this package, can unwrap the contained
 * keyring.
 */
public final class VerificationKeyring {

	private final PGPPublicKeyRingCollection publicKeys;

	/**
	 * Wraps the given public keyring collection. Package-private so only
	 * {@link SignatureService} can construct instances.
	 * @param publicKeys the underlying PGP public keyring collection
	 */
	VerificationKeyring(PGPPublicKeyRingCollection publicKeys) {
		this.publicKeys = publicKeys;
	}

	/**
	 * Returns the wrapped PGP public keyring collection. Package-private so the
	 * BouncyCastle type stays internal to this package.
	 * @return the underlying PGP public keyring collection
	 */
	PGPPublicKeyRingCollection pgpPublicKeys() {
		return this.publicKeys;
	}

}
