package org.alexmond.jhelm.gotemplate;

/**
 * Exception thrown when a template function fails during execution. Carries the function
 * name for diagnostic context.
 */
public class FunctionExecutionException extends RuntimeException {

	private final String functionName;

	public FunctionExecutionException(String message) {
		super(message);
		this.functionName = null;
	}

	public FunctionExecutionException(String message, Throwable cause) {
		super(message, cause);
		this.functionName = null;
	}

	public FunctionExecutionException(String functionName, String message, Throwable cause) {
		super(functionName + ": " + message, cause);
		this.functionName = functionName;
	}

	public String getFunctionName() {
		return this.functionName;
	}

}
