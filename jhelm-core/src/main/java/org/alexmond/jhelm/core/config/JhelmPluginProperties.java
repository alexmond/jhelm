package org.alexmond.jhelm.core.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for loading jhelm Java plugins from external JAR files that are not on
 * the application classpath. Distinct from the native-Helm plugin store
 * ({@code $HELM_PLUGINS}) and the WASM {@code .jhp} store — this covers Java plugins
 * built against {@code jhelm-plugin-api}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jhelm.plugins")
public class JhelmPluginProperties {

	/**
	 * Directories scanned for external plugin JARs. Every {@code *.jar} in each directory
	 * is loaded in its own class loader and its declared plugin services are discovered.
	 * Empty by default, so external-JAR loading is off unless a directory is configured.
	 */
	private List<String> path = new ArrayList<>();

}
