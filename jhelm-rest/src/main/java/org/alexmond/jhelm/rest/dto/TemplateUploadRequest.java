package org.alexmond.jhelm.rest.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Metadata accompanying an uploaded {@code .tgz} chart archive when rendering templates.
 */
@Data
@Schema(description = "Metadata for rendering templates from an uploaded .tgz chart archive")
public class TemplateUploadRequest {

	@Schema(description = "Release name for template rendering", example = "my-release", defaultValue = "RELEASE-NAME")
	private String releaseName = "RELEASE-NAME";

	@Schema(description = "Kubernetes namespace", example = "default", defaultValue = "default")
	private String namespace = "default";

	@Schema(description = "Override values for the chart")
	private Map<String, Object> values;

	@Schema(description = "Keep only documents rendered from these template paths (e.g. templates/deployment.yaml)")
	private List<String> showOnly;

	@Schema(description = "Drop chart test hooks from the output", defaultValue = "false")
	private boolean skipTests;

	@Schema(description = "Include the chart's crds/ manifests in the output", defaultValue = "false")
	private boolean includeCrds;

	@Schema(description = "Render with .Release.IsUpgrade instead of .Release.IsInstall", defaultValue = "false")
	@JsonProperty("isUpgrade")
	private boolean isUpgrade;

}
