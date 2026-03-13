package org.alexmond.jhelm.rest.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Metadata for installing a release from an uploaded .tgz chart archive")
public class InstallUploadRequest {

	@Schema(description = "Name for the release", example = "my-release", requiredMode = Schema.RequiredMode.REQUIRED)
	private String releaseName;

	@Schema(description = "Kubernetes namespace", example = "default", defaultValue = "default")
	private String namespace = "default";

	@Schema(description = "Override values for the chart")
	private Map<String, Object> values;

	@Schema(description = "Simulate an install without applying", defaultValue = "false")
	private boolean dryRun;

}
