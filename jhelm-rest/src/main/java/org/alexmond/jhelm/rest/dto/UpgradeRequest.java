package org.alexmond.jhelm.rest.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.alexmond.jhelm.core.action.UpgradeValueStrategy;

/**
 * Request body for upgrading an existing release from a repository chart reference.
 */
@Data
@Schema(description = "Request to upgrade an existing Helm release from a repository chart reference")
public class UpgradeRequest {

	@Schema(description = "Chart reference (repo/chart or oci://...)", example = "bitnami/nginx",
			requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "chartRef is required")
	private String chartRef;

	@Schema(description = "Chart version", example = "18.3.1")
	private String version;

	@Schema(description = "Override values for the chart")
	private Map<String, Object> values;

	@Schema(description = "How prior release values are combined with the supplied overrides: "
			+ "DEFAULT (reuse prior values only when no overrides are given), RESET (discard prior "
			+ "values), REUSE (merge overrides onto prior values), or RESET_THEN_REUSE (merge onto "
			+ "prior values but render against the new chart defaults). Mirrors helm upgrade's "
			+ "--reset-values / --reuse-values / --reset-then-reuse-values.", defaultValue = "DEFAULT")
	private UpgradeValueStrategy valueStrategy;

	@Schema(description = "Simulate an upgrade without applying", defaultValue = "false")
	private boolean dryRun;

}
