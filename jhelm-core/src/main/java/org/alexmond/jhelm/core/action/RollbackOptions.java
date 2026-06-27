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

}
