package org.alexmond.jhelm.core.exception;

/**
 * Thrown when storing or retrieving release data from the cluster fails.
 */
public class ReleaseStorageException extends JhelmException {

	public ReleaseStorageException(String message) {
		super(message);
	}

	public ReleaseStorageException(String message, Throwable cause) {
		super(message, cause);
	}

}
