package org.alexmond.jhelm.core.action;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.Release;

/**
 * Immutable options for {@link UpgradeAction#upgrade(UpgradeOptions)}.
 * <p>
 * Built via the Lombok builder; unset fields fall back to defaults that reproduce Helm's
 * standard upgrade behavior (DEFAULT value strategy, no dry run, hooks enabled, history
 * capped at 10 revisions, empty value overrides).
 */
@Getter
@Builder
public final class UpgradeOptions {

	/**
	 * The currently deployed release (its chart defaults and persisted user values feed
	 * the reuse strategies).
	 */
	private final Release currentRelease;

	/** The chart to upgrade to. */
	private final Chart newChart;

	/**
	 * This command's value overrides, merged according to the value strategy. Defaults to
	 * an empty map.
	 */
	@Builder.Default
	private final Map<String, Object> values = Map.of();

	/**
	 * How to resolve the previous user values against the overrides. Defaults to
	 * {@link UpgradeValueStrategy#DEFAULT}.
	 */
	@Builder.Default
	private final UpgradeValueStrategy valueStrategy = UpgradeValueStrategy.DEFAULT;

	/**
	 * When {@code true}, render only without applying to the cluster. Defaults to
	 * {@code false}.
	 */
	private final boolean dryRun;

	/**
	 * When {@code true}, skip running pre-upgrade and post-upgrade hooks. Defaults to
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
	 * When {@code true}, force resource updates through a delete-and-recreate strategy
	 * (Helm {@code --force}): existing resources are deleted before the new manifest is
	 * applied, rather than patched in place. Defaults to {@code false}.
	 */
	private final boolean force;

}
