package org.alexmond.jhelm.core.exception;

/**
 * Base exception for all jhelm operations. Extends {@link RuntimeException} so that
 * callers are not forced to declare checked exceptions — consistent with Spring's
 * exception philosophy.
 */
public class JhelmException extends RuntimeException {

	public JhelmException(String message) {
		super(message);
	}

	public JhelmException(String message, Throwable cause) {
		super(message, cause);
	}

}
