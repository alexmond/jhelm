package org.alexmond.jhelm.core.service;

import java.util.List;

/**
 * Thrown when user-supplied values fail JSON Schema validation against a chart's
 * {@code values.schema.json}.
 */
public class SchemaValidationException extends Exception {

	private final List<String> validationErrors;

	public SchemaValidationException(String chartName, List<String> errors) {
		super(buildMessage(chartName, errors));
		this.validationErrors = List.copyOf(errors);
	}

	/**
	 * Returns the individual validation error messages.
	 * @return unmodifiable list of error messages
	 */
	public List<String> getValidationErrors() {
		return validationErrors;
	}

	private static String buildMessage(String chartName, List<String> errors) {
		StringBuilder sb = new StringBuilder();
		sb.append("values don't meet the specifications of the schema(s) in the following chart(s):\n");
		sb.append(chartName).append(":\n");
		for (String e : errors) {
			sb.append("- ").append(e).append("\n");
		}
		return sb.toString();
	}

}
