package org.alexmond.jhelm.rest;

/**
 * Thrown when a requested resource (typically a release) does not exist, so the REST
 * layer can map it to {@code 404 Not Found} rather than the {@code 400} that a generic
 * {@link IllegalArgumentException} would produce.
 */
public class NotFoundException extends RuntimeException {

	/**
	 * Creates the exception.
	 * @param message the human-readable detail describing what was not found
	 */
	public NotFoundException(String message) {
		super(message);
	}

}
