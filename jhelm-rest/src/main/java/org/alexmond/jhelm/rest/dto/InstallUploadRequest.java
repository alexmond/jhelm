package org.alexmond.jhelm.rest.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Metadata accompanying an uploaded {@code .tgz} chart archive when installing a release.
 */
@Data
@Schema(description = "Metadata for installing a release from an uploaded .tgz chart archive")
public class InstallUploadRequest {

	@Schema(description = "Name for the release", example = "my-release", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "releaseName is required")
	private String releaseName;

	@Schema(description = "Kubernetes namespace", example = "default", defaultValue = "default")
	private String namespace = "default";

	@Schema(description = "Override values for the chart")
	private Map<String, Object> values;

	@Schema(description = "Simulate an install without applying", defaultValue = "false")
	private boolean dryRun;

	@Schema(description = "Custom release description (stored on the release)")
	private String description;

	@Schema(description = "Custom labels to store on the release Secret")
	private Map<String, String> labels;

}
