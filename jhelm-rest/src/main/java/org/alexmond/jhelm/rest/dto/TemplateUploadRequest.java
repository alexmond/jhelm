package org.alexmond.jhelm.rest.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Metadata for rendering templates from an uploaded .tgz chart archive")
public class TemplateUploadRequest {

	@Schema(description = "Release name for template rendering", example = "my-release", defaultValue = "RELEASE-NAME")
	private String releaseName = "RELEASE-NAME";

	@Schema(description = "Kubernetes namespace", example = "default", defaultValue = "default")
	private String namespace = "default";

	@Schema(description = "Override values for the chart")
	private Map<String, Object> values;

}
