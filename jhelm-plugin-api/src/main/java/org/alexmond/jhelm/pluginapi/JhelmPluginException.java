package org.alexmond.jhelm.pluginapi;

/**
 * Thrown by a jhelm plugin when it fails to carry out its work (a post-render transform,
 * a chart download, a template-function call). jhelm surfaces the message to the user
 * and, for a failed operation, aborts it.
 */
public class JhelmPluginException extends Exception {

	/**
	 * Creates an exception with a message.
	 * @param message the failure description
	 */
	public JhelmPluginException(String message) {
		super(message);
	}

	/**
	 * Creates an exception with a message and cause.
	 * @param message the failure description
	 * @param cause the underlying cause
	 */
	public JhelmPluginException(String message, Throwable cause) {
		super(message, cause);
	}

}
