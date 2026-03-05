package org.alexmond.jhelm.rest.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to upgrade an existing Helm release")
public class UpgradeRequest {

	@Schema(description = "Path to the new chart directory", example = "/tmp/nginx-v2",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String chartPath;

	@Schema(description = "Override values for the chart")
	private Map<String, Object> values;

	@Schema(description = "Simulate an upgrade without applying", defaultValue = "false")
	private boolean dryRun;

}
