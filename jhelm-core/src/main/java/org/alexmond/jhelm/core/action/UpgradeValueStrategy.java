package org.alexmond.jhelm.core.action;

/**
 * Controls how user-supplied values are resolved when upgrading a release, mirroring
 * Helm's value-reuse flags.
 */
public enum UpgradeValueStrategy {

	/**
	 * Helm's default behaviour (no value flag). When this command supplies overrides,
	 * only those overrides are used (over the new chart defaults); when no overrides are
	 * given, the previous release's user values are reused.
	 */
	DEFAULT,

	/**
	 * Maps to {@code --reset-values}. Discards the previous release's user values and
	 * resets to the new chart defaults, applying only this command's overrides.
	 */
	RESET,

	/**
	 * Maps to {@code --reuse-values}. Reuses the previous release's user values, layering
	 * this command's overrides on top, rendered against the previous chart's defaults so
	 * new default changes are ignored.
	 */
	REUSE,

	/**
	 * Maps to {@code --reset-then-reuse-values}. Reuses the previous release's user
	 * values with this command's overrides on top, but renders against the new chart
	 * defaults so new default changes are picked up.
	 */
	RESET_THEN_REUSE

}
