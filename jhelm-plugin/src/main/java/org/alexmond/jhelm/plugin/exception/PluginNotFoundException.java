package org.alexmond.jhelm.plugin.exception;

/**
 * Thrown when a named plugin is not found in the plugin registry.
 */
public class PluginNotFoundException extends PluginException {

	/**
	 * Create a not-found exception with a detail message.
	 * @param message the detail message
	 */
	public PluginNotFoundException(String message) {
		super(message);
	}

	/**
	 * Create a not-found exception with a detail message and cause.
	 * @param message the detail message
	 * @param cause the underlying cause
	 */
	public PluginNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

}
