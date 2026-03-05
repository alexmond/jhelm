package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to resolve or download chart dependencies")
public class DependencyResolveRequest {

	@Schema(description = "Path to the chart directory", example = "/tmp/my-chart",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String chartPath;

}
