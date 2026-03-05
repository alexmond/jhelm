package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to rollback a release to a previous revision")
public class RollbackRequest {

	@Schema(description = "Revision number to rollback to", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
	private int revision;

}
