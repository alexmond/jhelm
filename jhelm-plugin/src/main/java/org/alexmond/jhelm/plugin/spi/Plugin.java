package org.alexmond.jhelm.plugin.spi;

import org.alexmond.jhelm.plugin.model.PluginType;

/**
 * Core plugin interface. All plugin types implement this.
 */
public interface Plugin extends AutoCloseable {

	/**
	 * Return the plugin name.
	 * @return the unique plugin name as declared in its manifest
	 */
	String name();

	/**
	 * Return the plugin type.
	 * @return the {@link PluginType} this plugin implements
	 */
	PluginType type();

	@Override
	void close();

}
