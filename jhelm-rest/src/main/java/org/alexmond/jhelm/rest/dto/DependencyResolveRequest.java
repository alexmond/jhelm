package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to resolve chart dependencies")
public class DependencyResolveRequest {

	@Schema(description = "Chart reference: repo/chart or oci://...", example = "bitnami/nginx",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String chartRef;

	@Schema(description = "Chart version", example = "18.3.1")
	private String version;

}
