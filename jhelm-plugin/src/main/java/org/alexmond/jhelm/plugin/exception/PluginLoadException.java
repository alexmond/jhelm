package org.alexmond.jhelm.plugin.exception;

/**
 * Thrown when a WASM module fails to load (corrupt binary, missing exports, etc.).
 */
public class PluginLoadException extends PluginException {

	/**
	 * Create a load exception with a detail message.
	 * @param message the detail message
	 */
	public PluginLoadException(String message) {
		super(message);
	}

	/**
	 * Create a load exception with a detail message and cause.
	 * @param message the detail message
	 * @param cause the underlying cause
	 */
	public PluginLoadException(String message, Throwable cause) {
		super(message, cause);
	}

}
