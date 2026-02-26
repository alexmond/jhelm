package org.alexmond.jhelm.plugin.exception;

/**
 * Base exception for all plugin operations.
 */
public class PluginException extends Exception {

	public PluginException(String message) {
		super(message);
	}

	public PluginException(String message, Throwable cause) {
		super(message, cause);
	}

}
