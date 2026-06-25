package org.alexmond.jhelm.plugin.exception;

/**
 * Thrown when a WASM function invocation fails (trap, memory violation, etc.).
 */
public class PluginExecutionException extends PluginException {

	/**
	 * Create an execution exception with a detail message.
	 * @param message the detail message
	 */
	public PluginExecutionException(String message) {
		super(message);
	}

	/**
	 * Create an execution exception with a detail message and cause.
	 * @param message the detail message
	 * @param cause the underlying cause
	 */
	public PluginExecutionException(String message, Throwable cause) {
		super(message, cause);
	}

}
