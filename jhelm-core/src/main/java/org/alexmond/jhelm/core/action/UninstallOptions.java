package org.alexmond.jhelm.core.action;

import lombok.Builder;
import lombok.Getter;

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

}
