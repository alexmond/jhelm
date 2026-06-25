package org.alexmond.jhelm.core.exception;

/**
 * Exception thrown when a PGP signing or verification operation fails for reasons other
 * than a rejected signature — for example when a keyring cannot be read, a signing key
 * cannot be located, or the underlying OpenPGP library reports an error. Wraps the
 * checked {@code IOException}/{@code PGPException} thrown by the signing layer so that
 * the BouncyCastle exception types stay off the public API.
 */
public class SignatureException extends JhelmException {

	/**
	 * Creates a signature failure with the given detail message.
	 * @param message a description of why the operation failed
	 */
	public SignatureException(String message) {
		super(message);
	}

	/**
	 * Creates a signature failure wrapping an underlying cause.
	 * @param message a description of why the operation failed
	 * @param cause the underlying error being wrapped
	 */
	public SignatureException(String message, Throwable cause) {
		super(message, cause);
	}

}
