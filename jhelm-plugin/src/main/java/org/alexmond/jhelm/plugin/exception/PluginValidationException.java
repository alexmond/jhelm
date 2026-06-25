package org.alexmond.jhelm.plugin.exception;

/**
 * Thrown when a plugin manifest is malformed or a cryptographic signature is invalid.
 */
public class PluginValidationException extends PluginException {

	/**
	 * Create a validation exception with a detail message.
	 * @param message the detail message
	 */
	public PluginValidationException(String message) {
		super(message);
	}

	/**
	 * Create a validation exception with a detail message and cause.
	 * @param message the detail message
	 * @param cause the underlying cause
	 */
	public PluginValidationException(String message, Throwable cause) {
		super(message, cause);
	}

}
