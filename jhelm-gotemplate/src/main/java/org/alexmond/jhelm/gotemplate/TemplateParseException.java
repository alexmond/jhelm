package org.alexmond.jhelm.gotemplate;

public class TemplateParseException extends TemplateException {

	public TemplateParseException(String message) {
		super(message);
	}

	public TemplateParseException(String message, Throwable cause) {
		super(message, cause);
	}

	public TemplateParseException(String message, int line, int column) {
		super(message, line, column);
	}

}
