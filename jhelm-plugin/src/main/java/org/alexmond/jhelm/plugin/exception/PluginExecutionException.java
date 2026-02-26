package org.alexmond.jhelm.plugin.exception;

/**
 * Thrown when a WASM function invocation fails (trap, memory violation, etc.).
 */
public class PluginExecutionException extends PluginException {

	public PluginExecutionException(String message) {
		super(message);
	}

	public PluginExecutionException(String message, Throwable cause) {
		super(message, cause);
	}

}
