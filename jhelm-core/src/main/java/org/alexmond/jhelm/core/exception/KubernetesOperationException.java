package org.alexmond.jhelm.core.exception;

/**
 * Thrown when a Kubernetes API operation (apply, delete, etc.) fails. Wraps the
 * underlying API error and includes the HTTP status code when available.
 */
public class KubernetesOperationException extends JhelmException {

	private final int statusCode;

	/**
	 * Creates an exception with no associated HTTP status code (status reported as
	 * {@code -1}).
	 * @param message a description of the failed operation
	 */
	public KubernetesOperationException(String message) {
		super(message);
		this.statusCode = -1;
	}

	/**
	 * Creates an exception wrapping an underlying cause, with no associated HTTP status
	 * code.
	 * @param message a description of the failed operation
	 * @param cause the underlying API error
	 */
	public KubernetesOperationException(String message, Throwable cause) {
		super(message, cause);
		this.statusCode = -1;
	}

	/**
	 * Creates an exception wrapping an underlying cause and the HTTP status code returned
	 * by the Kubernetes API.
	 * @param message a description of the failed operation
	 * @param cause the underlying API error
	 * @param statusCode the HTTP status code returned by the API
	 */
	public KubernetesOperationException(String message, Throwable cause, int statusCode) {
		super(message, cause);
		this.statusCode = statusCode;
	}

	/**
	 * Returns the HTTP status code reported by the Kubernetes API, or {@code -1} if
	 * unknown.
	 * @return the HTTP status code, or {@code -1}
	 */
	public int getStatusCode() {
		return statusCode;
	}

	/**
	 * Returns {@code true} if the error is transient and may succeed on retry (5xx server
	 * errors and 429 rate-limiting).
	 * @return {@code true} if the operation may succeed on retry
	 */
	public boolean isTransient() {
		return statusCode == 429 || (statusCode >= 500 && statusCode < 600);
	}

}
