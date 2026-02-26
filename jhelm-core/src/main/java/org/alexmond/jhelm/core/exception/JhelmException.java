package org.alexmond.jhelm.core.exception;

/**
 * Base exception for all jhelm operations.
 */
public class JhelmException extends Exception {

	public JhelmException(String message) {
		super(message);
	}

	public JhelmException(String message, Throwable cause) {
		super(message, cause);
	}

}
