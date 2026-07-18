package org.alexmond.jhelm.pluginapi;

/**
 * A plugin that reacts to release lifecycle events (install, upgrade, uninstall — each
 * with a pre and post phase). Useful for notifications, auditing, or external
 * integrations.
 *
 * <p>
 * A listener should be quick and side-effect-tolerant: an exception from a {@code POST_*}
 * phase is logged and ignored so it cannot corrupt a completed operation.
 */
public interface JhelmLifecycleListener extends JhelmPlugin {

	/**
	 * Handles a lifecycle event.
	 * @param event the event (phase, release, namespace, metadata)
	 */
	void onEvent(JhelmReleaseEvent event);

}
