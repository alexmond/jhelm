package org.alexmond.jhelm.plugin.exception;

import org.alexmond.jhelm.core.exception.JhelmException;

/**
 * Base exception for all plugin operations.
 */
public class PluginException extends JhelmException {

	/**
	 * Create a plugin exception with a detail message.
	 * @param message the detail message
	 */
	public PluginException(String message) {
		super(message);
	}

	/**
	 * Create a plugin exception with a detail message and cause.
	 * @param message the detail message
	 * @param cause the underlying cause
	 */
	public PluginException(String message, Throwable cause) {
		super(message, cause);
	}

}
