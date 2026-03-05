package org.alexmond.jhelm.rest.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to lint/validate a chart")
public class LintRequest {

	@Schema(description = "Path to the chart directory", example = "/tmp/nginx",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String chartPath;

	@Schema(description = "Override values for validation")
	private Map<String, Object> values;

	@Schema(description = "Enable strict linting mode", defaultValue = "false")
	private boolean strict;

}
