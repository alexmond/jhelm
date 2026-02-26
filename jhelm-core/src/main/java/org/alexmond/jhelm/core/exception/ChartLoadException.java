package org.alexmond.jhelm.core.exception;

import lombok.Getter;

/**
 * Exception thrown when a chart cannot be loaded. Provides contextual information
 * including the chart path and an actionable suggestion for resolving the error.
 */
@Getter
public class ChartLoadException extends JhelmException {

	private final String chartPath;

	private final String suggestion;

	public ChartLoadException(String message, String chartPath, String suggestion) {
		super(buildMessage(message, chartPath, suggestion));
		this.chartPath = chartPath;
		this.suggestion = suggestion;
	}

	public ChartLoadException(String message, Throwable cause, String chartPath, String suggestion) {
		super(buildMessage(message, chartPath, suggestion), cause);
		this.chartPath = chartPath;
		this.suggestion = suggestion;
	}

	private static String buildMessage(String message, String chartPath, String suggestion) {
		StringBuilder sb = new StringBuilder(message);
		if (chartPath != null) {
			sb.append(" [path: ").append(chartPath).append("]");
		}
		if (suggestion != null) {
			sb.append(". Suggestion: ").append(suggestion);
		}
		return sb.toString();
	}

}
