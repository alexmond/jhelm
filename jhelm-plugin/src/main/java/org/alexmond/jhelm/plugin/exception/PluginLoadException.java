package org.alexmond.jhelm.plugin.exception;

/**
 * Thrown when a WASM module fails to load (corrupt binary, missing exports, etc.).
 */
public class PluginLoadException extends PluginException {

	public PluginLoadException(String message) {
		super(message);
	}

	public PluginLoadException(String message, Throwable cause) {
		super(message, cause);
	}

}
