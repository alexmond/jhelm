package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to scaffold a new chart")
public class CreateRequest {

	@Schema(description = "Path where the chart will be created", example = "/tmp/my-chart",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String chartPath;

}
