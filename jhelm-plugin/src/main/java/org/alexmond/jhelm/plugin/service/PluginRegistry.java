package org.alexmond.jhelm.plugin.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.alexmond.jhelm.plugin.model.PluginDescriptor;
import org.alexmond.jhelm.plugin.model.PluginType;

/**
 * Thread-safe in-memory registry of loaded plugins.
 */
public class PluginRegistry {

	private final Map<String, PluginDescriptor> plugins = new ConcurrentHashMap<>();

	/**
	 * Register a plugin descriptor.
	 * @param descriptor the plugin descriptor
	 */
	public void register(PluginDescriptor descriptor) {
		plugins.put(descriptor.getManifest().getName(), descriptor);
	}

	/**
	 * Remove a plugin by name.
	 * @param name the plugin name
	 * @return the removed descriptor, or empty
	 */
	public Optional<PluginDescriptor> unregister(String name) {
		PluginDescriptor removed = plugins.remove(name);
		return Optional.ofNullable(removed);
	}

	/**
	 * Get a plugin by name.
	 * @param name the plugin name
	 * @return the descriptor, or empty
	 */
	public Optional<PluginDescriptor> get(String name) {
		return Optional.ofNullable(plugins.get(name));
	}

	/**
	 * List all registered plugins.
	 * @return unmodifiable list of all descriptors
	 */
	public List<PluginDescriptor> listAll() {
		return Collections.unmodifiableList(new ArrayList<>(plugins.values()));
	}

	/**
	 * List plugins of a specific type.
	 * @param type the plugin type
	 * @return list of matching descriptors
	 */
	public List<PluginDescriptor> listByType(PluginType type) {
		return plugins.values().stream().filter((d) -> d.getManifest().getType() == type).toList();
	}

}
