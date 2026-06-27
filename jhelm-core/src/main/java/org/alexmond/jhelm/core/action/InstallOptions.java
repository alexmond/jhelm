package org.alexmond.jhelm.core.action;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import org.alexmond.jhelm.core.model.Chart;

/**
 * Immutable options for {@link InstallAction#install(InstallOptions)}.
 * <p>
 * Built via the Lombok builder; unset fields fall back to defaults that reproduce Helm's
 * standard install behavior (revision 1, no dry run, hooks enabled, empty value
 * overrides).
 */
@Getter
@Builder
public final class InstallOptions {

	/** The chart to install (must not be a library chart). */
	private final Chart chart;

	/** The name to give the release. */
	private final String releaseName;

	/** The target namespace. */
	private final String namespace;

	/**
	 * User-supplied values merged over the chart defaults. Defaults to an empty map.
	 */
	@Builder.Default
	private final Map<String, Object> values = Map.of();

	/** The release revision number to assign. Defaults to {@code 1}. */
	@Builder.Default
	private final int revision = 1;

	/**
	 * When {@code true}, render only and skip applying anything to the cluster. Defaults
	 * to {@code false}.
	 */
	private final boolean dryRun;

	/**
	 * When {@code true}, skip running pre-install and post-install hooks. Defaults to
	 * {@code false}.
	 */
	private final boolean noHooks;

}
