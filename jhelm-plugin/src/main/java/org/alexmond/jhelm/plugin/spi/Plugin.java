package org.alexmond.jhelm.plugin.spi;

import org.alexmond.jhelm.plugin.model.PluginType;

/**
 * Core plugin interface. All plugin types implement this.
 */
public interface Plugin extends AutoCloseable {

	/**
	 * Return the plugin name.
	 */
	String name();

	/**
	 * Return the plugin type.
	 */
	PluginType type();

	@Override
	void close();

}
