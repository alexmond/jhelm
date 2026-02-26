package org.alexmond.jhelm.plugin.model;

import lombok.Builder;
import lombok.Data;
import org.alexmond.jhelm.plugin.spi.Plugin;

/**
 * Runtime descriptor that pairs a plugin manifest with its loaded instance.
 */
@Data
@Builder
public class PluginDescriptor {

	private final PluginManifest manifest;

	private final Plugin plugin;

}
