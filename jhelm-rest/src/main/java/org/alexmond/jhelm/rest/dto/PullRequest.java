package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to pull a chart from a repository or OCI registry")
public class PullRequest {

	@Schema(description = "Chart reference: repo/chart, repo/chart:version, or oci://...", example = "bitnami/nginx",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String chart;

	@Schema(description = "Chart version (required for repo charts, optional for OCI)", example = "18.3.1")
	private String version;

}
