package org.alexmond.jhelm.rest.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Request body for scaffolding a new chart skeleton.
 */
@Data
@Schema(description = "Request to scaffold a new chart")
public class CreateRequest {

	@Schema(description = "Name of the chart to create", example = "my-chart",
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "name is required")
	private String name;

}
