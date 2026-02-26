package org.alexmond.jhelm.plugin.model;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * Lifecycle event model passed to hook plugins.
 */
@Data
@Builder
public class PluginEvent {

	private final String phase;

	private final String releaseName;

	private final String namespace;

	private final Map<String, Object> metadata;

}
