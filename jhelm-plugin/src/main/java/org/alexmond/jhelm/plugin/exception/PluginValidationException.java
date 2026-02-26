package org.alexmond.jhelm.plugin.exception;

/**
 * Thrown when a plugin manifest is malformed or a cryptographic signature is invalid.
 */
public class PluginValidationException extends PluginException {

	public PluginValidationException(String message) {
		super(message);
	}

	public PluginValidationException(String message, Throwable cause) {
		super(message, cause);
	}

}
