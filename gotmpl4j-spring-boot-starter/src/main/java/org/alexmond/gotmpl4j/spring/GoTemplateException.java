package org.alexmond.gotmpl4j.spring;

/**
 * Unchecked wrapper for the engine's checked template exceptions, so
 * {@link GoTemplateService#render} stays convenient to call from Spring code.
 */
public class GoTemplateException extends RuntimeException {

	public GoTemplateException(String message, Throwable cause) {
		super(message, cause);
	}

}
