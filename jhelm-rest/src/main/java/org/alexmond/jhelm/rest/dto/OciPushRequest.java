package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to push a chart to an OCI registry")
public class OciPushRequest {

	@Schema(description = "Path to the chart .tgz archive", example = "/tmp/nginx-1.0.0.tgz",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String chartTgzPath;

	@Schema(description = "OCI URL to push the chart to", example = "oci://registry.example.com/charts/nginx",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String ociUrl;

}
