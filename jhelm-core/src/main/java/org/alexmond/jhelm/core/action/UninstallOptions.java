package org.alexmond.jhelm.core.action;

import lombok.Builder;
import lombok.Getter;
import org.alexmond.jhelm.core.service.CascadePolicy;

/**
 * Immutable options for {@link UninstallAction#uninstall(UninstallOptions)}.
 * <p>
 * Built via the Lombok builder; an unset {@code noHooks} flag defaults to {@code false},
 * reproducing Helm's standard uninstall behavior of running pre-delete and post-delete
 * hooks.
 */
@Getter
@Builder
public final class UninstallOptions {

	/** The name of the release to remove. */
	private final String releaseName;

	/** The namespace the release lives in. */
	private final String namespace;

	/**
	 * When {@code true}, skip running pre-delete and post-delete hooks. Defaults to
	 * {@code false}.
	 */
	private final boolean noHooks;

	/**
	 * When {@code true}, retain the release history and mark the release uninstalled
	 * instead of deleting its history. Defaults to {@code false}.
	 */
	private final boolean keepHistory;

	/**
	 * When {@code true}, simulate the uninstall without deleting anything or touching the
	 * release history (Helm {@code --dry-run}). Defaults to {@code false}.
	 */
	private final boolean dryRun;

	/**
	 * When {@code true}, wait until the release's resources are removed from the cluster
	 * before returning (Helm {@code --wait}). Defaults to {@code false}.
	 */
	private final boolean wait;

	/**
	 * Seconds to wait when {@link #wait} is set. Defaults to {@code 300}.
	 */
	@Builder.Default
	private final int timeout = 300;

	/**
	 * The deletion propagation policy (Helm {@code --cascade}). Defaults to
	 * {@link CascadePolicy#BACKGROUND}.
	 */
	@Builder.Default
	private final CascadePolicy cascade = CascadePolicy.BACKGROUND;

	/**
	 * Custom description stored on the uninstalled release when history is kept; falls
	 * back to {@code "Uninstallation complete"} when blank.
	 */
	private final String description;

}
