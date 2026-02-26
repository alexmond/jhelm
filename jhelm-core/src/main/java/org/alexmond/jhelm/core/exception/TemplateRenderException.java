package org.alexmond.jhelm.core.exception;

import lombok.Getter;

/**
 * Exception thrown when a template rendering error occurs. Provides contextual
 * information including the chart name and template name to help diagnose the source of
 * the error.
 */
@Getter
public class TemplateRenderException extends RuntimeException {

	private final String chartName;

	private final String templateName;

	public TemplateRenderException(String message, Throwable cause, String chartName, String templateName) {
		super(buildMessage(message, chartName, templateName), cause);
		this.chartName = chartName;
		this.templateName = templateName;
	}

	public TemplateRenderException(String message, String chartName, String templateName) {
		super(buildMessage(message, chartName, templateName));
		this.chartName = chartName;
		this.templateName = templateName;
	}

	private static String buildMessage(String message, String chartName, String templateName) {
		StringBuilder sb = new StringBuilder();
		if (chartName != null) {
			sb.append("chart '").append(chartName).append("'");
		}
		if (templateName != null) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append("template '").append(templateName).append("'");
		}
		if (sb.length() > 0) {
			sb.append(": ");
		}
		sb.append(message);
		return sb.toString();
	}

}
