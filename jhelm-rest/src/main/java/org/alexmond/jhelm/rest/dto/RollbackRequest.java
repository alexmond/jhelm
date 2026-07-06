package org.alexmond.jhelm.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Request body for rolling a release back to a previous revision.
 */
@Data
@Schema(description = "Request to rollback a release to a previous revision")
public class RollbackRequest {

	@Schema(description = "Revision number to rollback to", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
	private int revision;

	@Schema(description = "Skip pre/post-rollback hooks", defaultValue = "false")
	private boolean noHooks;

	@Schema(description = "Maximum revisions to keep (0 = no limit)", defaultValue = "10")
	private int maxHistory = 10;

	@Schema(description = "Simulate without applying manifests or storing a revision", defaultValue = "false")
	private boolean dryRun;

	@Schema(description = "Delete and recreate the target resources instead of patching in place",
			defaultValue = "false")
	private boolean force;

	@Schema(description = "Delete resources created during the rollback if it fails", defaultValue = "false")
	private boolean cleanupOnFail;

	@Schema(description = "Rolling-restart the release's workloads after the rollback (deprecated in Helm)",
			defaultValue = "false")
	private boolean recreatePods;

	@Schema(description = "Wait until the rolled-back resources are ready", defaultValue = "false")
	private boolean wait;

	@Schema(description = "With wait, also wait for Jobs to complete", defaultValue = "false")
	private boolean waitForJobs;

	@Schema(description = "Timeout in seconds for wait", defaultValue = "300")
	private int timeout = 300;

}
