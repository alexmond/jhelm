package org.alexmond.jhelm.rest.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.Data;

/**
 * Request body for resolving a chart's dependency versions into a {@code Chart.lock}.
 */
@Data
@Schema(description = "Request to resolve chart dependencies")
public class DependencyResolveRequest {

	@Schema(description = "Chart reference: repo/chart or oci://...", example = "bitnami/nginx",
			requiredMode = RequiredMode.REQUIRED)
	@NotBlank(message = "chartRef is required")
	private String chartRef;

	@Schema(description = "Chart version", example = "18.3.1")
	private String version;

}
