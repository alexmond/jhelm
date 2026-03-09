package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to scaffold a new chart")
public class CreateRequest {

	@Schema(description = "Name of the chart to create", example = "my-chart",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String name;

}
