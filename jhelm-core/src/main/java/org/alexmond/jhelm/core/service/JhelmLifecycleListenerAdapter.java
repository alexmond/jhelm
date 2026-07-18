package org.alexmond.jhelm.core.service;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.alexmond.jhelm.pluginapi.JhelmLifecycleListener;
import org.alexmond.jhelm.pluginapi.JhelmReleaseEvent;

/**
 * Adapts a Java {@link JhelmLifecycleListener} plugin to the internal
 * {@link LifecycleListener}, mapping the internal {@link LifecyclePhase} to the public
 * {@link JhelmReleaseEvent.Phase} and delivering a {@link JhelmReleaseEvent}. A listener
 * that throws is logged and ignored so it cannot corrupt the release operation.
 */
@Slf4j
public class JhelmLifecycleListenerAdapter implements LifecycleListener {

	private final JhelmLifecycleListener plugin;

	/**
	 * Wraps a lifecycle-listener plugin.
	 * @param plugin the Java lifecycle-listener plugin
	 */
	public JhelmLifecycleListenerAdapter(JhelmLifecycleListener plugin) {
		this.plugin = plugin;
	}

	@Override
	public void onEvent(LifecyclePhase phase, String releaseName, String namespace, Map<String, Object> metadata) {
		JhelmReleaseEvent.Phase mapped = map(phase);
		if (mapped == null) {
			return;
		}
		try {
			this.plugin.onEvent(new JhelmReleaseEvent(mapped, releaseName, namespace, metadata));
		}
		catch (RuntimeException ex) {
			log.warn("lifecycle listener plugin '{}' failed on {}: {}", this.plugin.name(), phase, ex.getMessage());
		}
	}

	private static JhelmReleaseEvent.Phase map(LifecyclePhase phase) {
		return switch (phase) {
			case PRE_INSTALL -> JhelmReleaseEvent.Phase.PRE_INSTALL;
			case POST_INSTALL -> JhelmReleaseEvent.Phase.POST_INSTALL;
			case PRE_UPGRADE -> JhelmReleaseEvent.Phase.PRE_UPGRADE;
			case POST_UPGRADE -> JhelmReleaseEvent.Phase.POST_UPGRADE;
			case PRE_ROLLBACK -> JhelmReleaseEvent.Phase.PRE_ROLLBACK;
			case POST_ROLLBACK -> JhelmReleaseEvent.Phase.POST_ROLLBACK;
			case PRE_DELETE -> JhelmReleaseEvent.Phase.PRE_UNINSTALL;
			case POST_DELETE -> JhelmReleaseEvent.Phase.POST_UNINSTALL;
		};
	}

}
