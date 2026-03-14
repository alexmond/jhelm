package org.alexmond.jhelm.rest.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to upgrade an existing Helm release from a repository chart reference")
public class UpgradeRequest {

	@Schema(description = "Chart reference (repo/chart or oci://...)", example = "bitnami/nginx",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String chartRef;

	@Schema(description = "Chart version", example = "18.3.1")
	private String version;

	@Schema(description = "Override values for the chart")
	private Map<String, Object> values;

	@Schema(description = "Simulate an upgrade without applying", defaultValue = "false")
	private boolean dryRun;

}
