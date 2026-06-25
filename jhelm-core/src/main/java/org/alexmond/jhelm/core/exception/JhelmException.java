package org.alexmond.jhelm.core.exception;

/**
 * Base exception for all jhelm operations. Extends {@link RuntimeException} so that
 * callers are not forced to declare checked exceptions — consistent with Spring's
 * exception philosophy.
 */
public class JhelmException extends RuntimeException {

	/**
	 * Creates an exception with the given detail message.
	 * @param message a description of the failure
	 */
	public JhelmException(String message) {
		super(message);
	}

	/**
	 * Creates an exception with the given detail message and underlying cause.
	 * @param message a description of the failure
	 * @param cause the underlying error being wrapped
	 */
	public JhelmException(String message, Throwable cause) {
		super(message, cause);
	}

}
