package org.alexmond.jhelm.rest.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Request body for rendering chart templates from a repository chart reference.
 */
@Data
@Schema(description = "Request to render chart templates")
public class TemplateRequest {

	@Schema(description = "Chart reference (repo/chart or oci://...)", example = "bitnami/nginx",
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "chartRef is required")
	private String chartRef;

	@Schema(description = "Chart version constraint", example = "18.3.1")
	private String version;

	@Schema(description = "Release name for template rendering", example = "my-release", defaultValue = "RELEASE-NAME")
	private String releaseName = "RELEASE-NAME";

	@Schema(description = "Kubernetes namespace", example = "default", defaultValue = "default")
	private String namespace = "default";

	@Schema(description = "Override values for the chart")
	private Map<String, Object> values;

}
