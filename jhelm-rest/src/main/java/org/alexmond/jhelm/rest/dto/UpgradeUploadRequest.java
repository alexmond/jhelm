package org.alexmond.jhelm.rest.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Metadata for upgrading a release from an uploaded .tgz chart archive")
public class UpgradeUploadRequest {

	@Schema(description = "Override values for the chart")
	private Map<String, Object> values;

	@Schema(description = "Simulate an upgrade without applying", defaultValue = "false")
	private boolean dryRun;

}
