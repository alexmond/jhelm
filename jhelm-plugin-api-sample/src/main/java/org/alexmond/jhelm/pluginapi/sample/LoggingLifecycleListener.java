package org.alexmond.jhelm.pluginapi.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.alexmond.jhelm.pluginapi.JhelmLifecycleListener;
import org.alexmond.jhelm.pluginapi.JhelmReleaseEvent;

/**
 * Sample {@link JhelmLifecycleListener} that logs each release lifecycle event. A real
 * listener might send a notification, write an audit record, or trigger a webhook.
 */
public class LoggingLifecycleListener implements JhelmLifecycleListener {

	private static final Logger log = LoggerFactory.getLogger(LoggingLifecycleListener.class);

	@Override
	public String name() {
		return "logging";
	}

	@Override
	public void onEvent(JhelmReleaseEvent event) {
		log.info("release lifecycle: {} {}/{}", event.phase(), event.namespace(), event.releaseName());
	}

}
