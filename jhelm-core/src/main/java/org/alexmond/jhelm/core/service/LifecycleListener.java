package org.alexmond.jhelm.core.service;

import java.util.Map;

/**
 * Callback interface for lifecycle event listeners. Implementations are notified at
 * lifecycle points such as pre-install, post-install, pre-upgrade, etc.
 */
public interface LifecycleListener {

	/**
	 * Handle a lifecycle event.
	 * @param phase the lifecycle phase (e.g., "pre-install", "post-install")
	 * @param releaseName the release name
	 * @param namespace the target namespace
	 * @param metadata additional metadata about the event
	 * @throws Exception if handling fails
	 */
	void onEvent(String phase, String releaseName, String namespace, Map<String, Object> metadata) throws Exception;

}
