package org.alexmond.jhelm.rest.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.alexmond.jhelm.core.action.UpgradeValueStrategy;

/**
 * Metadata accompanying an uploaded {@code .tgz} chart archive when upgrading a release.
 */
@Data
@Schema(description = "Metadata for upgrading a release from an uploaded .tgz chart archive")
public class UpgradeUploadRequest {

	@Schema(description = "Override values for the chart")
	private Map<String, Object> values;

	@Schema(description = "How prior release values are combined with the supplied overrides: "
			+ "DEFAULT, RESET, REUSE, or RESET_THEN_REUSE. Mirrors helm upgrade's --reset-values / "
			+ "--reuse-values / --reset-then-reuse-values.", defaultValue = "DEFAULT")
	private UpgradeValueStrategy valueStrategy;

	@Schema(description = "Simulate an upgrade without applying", defaultValue = "false")
	private boolean dryRun;

}
