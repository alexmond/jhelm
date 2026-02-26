package org.alexmond.jhelm.plugin.exception;

/**
 * Thrown when WASM execution exceeds the configured timeout.
 */
public class PluginTimeoutException extends PluginException {

	public PluginTimeoutException(String message) {
		super(message);
	}

	public PluginTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}

}
