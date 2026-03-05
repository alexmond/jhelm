package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to add a chart repository")
public class RepoAddRequest {

	@Schema(description = "Repository name", example = "bitnami", requiredMode = Schema.RequiredMode.REQUIRED)
	private String name;

	@Schema(description = "Repository URL", example = "https://charts.bitnami.com/bitnami",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private String url;

}
