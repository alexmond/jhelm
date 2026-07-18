package org.alexmond.jhelm.pluginapi;

import java.util.Map;

/**
 * A release lifecycle event delivered to a {@link JhelmLifecycleListener}. Carries the
 * phase, the release it concerns, and free-form metadata (for example the revision).
 *
 * @param phase the lifecycle phase
 * @param releaseName the release name
 * @param namespace the release namespace
 * @param metadata additional, phase-specific details (never {@code null})
 */
public record JhelmReleaseEvent(Phase phase, String releaseName, String namespace, Map<String, Object> metadata) {

	/** Release lifecycle phases, fired before and after each mutating operation. */
	public enum Phase {

		/** Before an install. */
		PRE_INSTALL,
		/** After a successful install. */
		POST_INSTALL,
		/** Before an upgrade. */
		PRE_UPGRADE,
		/** After a successful upgrade. */
		POST_UPGRADE,
		/** Before a rollback. */
		PRE_ROLLBACK,
		/** After a successful rollback. */
		POST_ROLLBACK,
		/** Before an uninstall. */
		PRE_UNINSTALL,
		/** After a successful uninstall. */
		POST_UNINSTALL

	}

	/**
	 * Canonical constructor; defaults {@code metadata} to an empty map when {@code null}.
	 * @param phase the lifecycle phase
	 * @param releaseName the release name
	 * @param namespace the release namespace
	 * @param metadata additional details, or {@code null} for none
	 */
	public JhelmReleaseEvent {
		metadata = (metadata != null) ? Map.copyOf(metadata) : Map.of();
	}

}
