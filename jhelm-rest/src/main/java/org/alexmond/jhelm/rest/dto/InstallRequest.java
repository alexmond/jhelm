package org.alexmond.jhelm.rest.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Request body for installing a release from a repository chart reference.
 */
@Data
@Schema(description = "Request to install a new Helm release from a repository chart reference")
public class InstallRequest {

	@Schema(description = "Chart reference (repo/chart or oci://...)", example = "bitnami/nginx",
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "chartRef is required")
	private String chartRef;

	@Schema(description = "Chart version", example = "18.3.1")
	private String version;

	@Schema(description = "Name for the release", example = "my-release", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "releaseName is required")
	private String releaseName;

	@Schema(description = "Kubernetes namespace", example = "default", defaultValue = "default")
	private String namespace = "default";

	@Schema(description = "Override values for the chart")
	private Map<String, Object> values;

	@Schema(description = "Simulate an install without applying", defaultValue = "false")
	private boolean dryRun;

}
