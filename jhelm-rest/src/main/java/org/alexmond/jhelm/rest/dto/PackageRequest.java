package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to package a chart into a .tgz archive")
public class PackageRequest {

	@Schema(description = "Path to the chart directory", example = "/tmp/nginx",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String chartPath;

}
