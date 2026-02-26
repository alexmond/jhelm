package org.alexmond.jhelm.plugin.exception;

/**
 * Thrown when a named plugin is not found in the plugin registry.
 */
public class PluginNotFoundException extends PluginException {

	public PluginNotFoundException(String message) {
		super(message);
	}

	public PluginNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

}
