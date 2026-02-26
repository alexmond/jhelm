package org.alexmond.jhelm.core.exception;

import java.util.List;

import lombok.Getter;

/**
 * Thrown when user-supplied values fail JSON Schema validation against a chart's
 * {@code values.schema.json}.
 */
@Getter
public class SchemaValidationException extends JhelmException {

	private final List<String> validationErrors;

	public SchemaValidationException(String chartName, List<String> errors) {
		super(buildMessage(chartName, errors));
		this.validationErrors = List.copyOf(errors);
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
