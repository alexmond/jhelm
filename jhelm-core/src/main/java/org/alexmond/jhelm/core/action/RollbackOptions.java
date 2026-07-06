package org.alexmond.jhelm.core.action;

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable options for {@link RollbackAction#rollback(RollbackOptions)}.
 * <p>
 * Built via the Lombok builder; unset fields fall back to defaults that reproduce Helm's
 * standard rollback behavior (hooks enabled, history capped at 10 revisions). The target
 * {@code revision} is required and has no default.
 */
@Getter
@Builder
public final class RollbackOptions {

	/** The release name. */
	private final String releaseName;

	/** The namespace the release lives in. */
	private final String namespace;

	/** The revision number to roll back to (required). */
	private final int revision;

	/**
	 * When {@code true}, skip running pre-rollback and post-rollback hooks. Defaults to
	 * {@code false}.
	 */
	private final boolean noHooks;

	/**
	 * Maximum revisions to keep; {@code 0} = no limit (Helm {@code --history-max}).
	 * Defaults to {@code 10}.
	 */
	@Builder.Default
	private final int maxHistory = 10;

	/**
	 * When {@code true}, simulate the rollback without applying manifests or storing the
	 * new revision (Helm {@code --dry-run}). Defaults to {@code false}.
	 */
	private final boolean dryRun;

	/**
	 * When {@code true}, delete the target revision's resources and recreate them instead
	 * of patching in place (Helm {@code --force}). Defaults to {@code false}.
	 */
	private final boolean force;

	/**
	 * When {@code true}, delete any resources created during a failed rollback (Helm
	 * {@code --cleanup-on-fail}). Defaults to {@code false}.
	 */
	private final boolean cleanupOnFail;

	/**
	 * When {@code true}, trigger a rolling restart of the release's workloads after the
	 * rollback (Helm's deprecated {@code --recreate-pods}). Defaults to {@code false}.
	 */
	private final boolean recreatePods;

	/**
	 * When {@code true}, wait until the rolled-back resources are ready before returning
	 * (Helm {@code --wait}). Defaults to {@code false}.
	 */
	private final boolean wait;

	/**
	 * When {@code true}, additionally wait for Jobs to complete when {@link #wait} is set
	 * (Helm {@code --wait-for-jobs}). Defaults to {@code false}.
	 */
	private final boolean waitForJobs;

	/**
	 * Seconds to wait when {@link #wait} is set. Defaults to {@code 300}.
	 */
	@Builder.Default
	private final int timeout = 300;

}
