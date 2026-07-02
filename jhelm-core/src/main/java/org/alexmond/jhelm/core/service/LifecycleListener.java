package org.alexmond.jhelm.core.service;

import java.util.Map;

/**
 * Callback interface for lifecycle event listeners. Implementations are notified at
 * lifecycle points such as pre-install, post-install, pre-upgrade, etc.
 */
@FunctionalInterface
public interface LifecycleListener {

	/**
	 * Handle a lifecycle event.
	 * @param phase the lifecycle phase (e.g. {@link LifecyclePhase#PRE_INSTALL})
	 * @param releaseName the release name
	 * @param namespace the target namespace
	 * @param metadata additional metadata about the event
	 * @throws Exception if handling fails
	 */
	// S112: this is a user-facing lifecycle SPI; an implementation may fail in arbitrary
	// ways, so the broad throws is intentional rather than a generic-exception smell.
	@SuppressWarnings("java:S112")
	void onEvent(LifecyclePhase phase, String releaseName, String namespace, Map<String, Object> metadata)
			throws Exception;

}
