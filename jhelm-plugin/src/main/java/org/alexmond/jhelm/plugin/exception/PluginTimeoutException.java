package org.alexmond.jhelm.plugin.exception;

/**
 * Thrown when WASM execution exceeds the configured timeout.
 */
public class PluginTimeoutException extends PluginException {

	/**
	 * Create a timeout exception with a detail message.
	 * @param message the detail message
	 */
	public PluginTimeoutException(String message) {
		super(message);
	}

	/**
	 * Create a timeout exception with a detail message and cause.
	 * @param message the detail message
	 * @param cause the underlying cause
	 */
	public PluginTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}

}
