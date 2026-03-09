package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to pull a chart from a repository")
public class PullRequest {

	@Schema(description = "Chart version to pull", example = "1.0.0")
	private String version;

}
