package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to pull a chart from an OCI registry")
public class OciPullRequest {

	@Schema(description = "OCI URL of the chart", example = "oci://registry.example.com/charts/nginx:1.0.0",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String ociUrl;

}
