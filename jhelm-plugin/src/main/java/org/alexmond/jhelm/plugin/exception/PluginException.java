package org.alexmond.jhelm.plugin.exception;

import org.alexmond.jhelm.core.exception.JhelmException;

/**
 * Base exception for all plugin operations.
 */
public class PluginException extends JhelmException {

	public PluginException(String message) {
		super(message);
	}

	public PluginException(String message, Throwable cause) {
		super(message, cause);
	}

}
