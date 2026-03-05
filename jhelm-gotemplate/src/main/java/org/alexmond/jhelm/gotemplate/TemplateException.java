package org.alexmond.jhelm.gotemplate;

/**
 * Base exception for template parsing and execution errors. Optionally carries line and
 * column information for error location context.
 */
public class TemplateException extends Exception {

	private final int line;

	private final int column;

	public TemplateException(String message) {
		super(message);
		this.line = -1;
		this.column = -1;
	}

	public TemplateException(String message, Throwable cause) {
		super(message, cause);
		this.line = -1;
		this.column = -1;
	}

	public TemplateException(String message, int line, int column) {
		super(formatMessage(message, line, column));
		this.line = line;
		this.column = column;
	}

	public TemplateException(String message, int line, int column, Throwable cause) {
		super(formatMessage(message, line, column), cause);
		this.line = line;
		this.column = column;
	}

	/**
	 * Returns the line number where the error occurred, or -1 if unknown.
	 */
	public int getLine() {
		return this.line;
	}

	/**
	 * Returns the column number where the error occurred, or -1 if unknown.
	 */
	public int getColumn() {
		return this.column;
	}

	private static String formatMessage(String message, int line, int column) {
		if (line > 0 && column > 0) {
			return "line " + line + ":" + column + ": " + message;
		}
		if (line > 0) {
			return "line " + line + ": " + message;
		}
		return message;
	}

}
