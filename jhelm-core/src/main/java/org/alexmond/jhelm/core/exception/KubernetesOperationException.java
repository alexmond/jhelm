package org.alexmond.jhelm.core.exception;

/**
 * Thrown when a Kubernetes API operation (apply, delete, etc.) fails. Wraps the
 * underlying API error and includes the HTTP status code when available.
 */
public class KubernetesOperationException extends JhelmException {

	private final int statusCode;

	public KubernetesOperationException(String message) {
		super(message);
		this.statusCode = -1;
	}

	public KubernetesOperationException(String message, Throwable cause) {
		super(message, cause);
		this.statusCode = -1;
	}

	public KubernetesOperationException(String message, Throwable cause, int statusCode) {
		super(message, cause);
		this.statusCode = statusCode;
	}

	public int getStatusCode() {
		return statusCode;
	}

	/**
	 * Returns {@code true} if the error is transient and may succeed on retry (5xx server
	 * errors and 429 rate-limiting).
	 */
	public boolean isTransient() {
		return statusCode == 429 || (statusCode >= 500 && statusCode < 600);
	}

}
