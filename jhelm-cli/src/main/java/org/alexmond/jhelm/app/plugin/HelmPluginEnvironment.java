package org.alexmond.jhelm.app.plugin;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Builder;

/**
 * Builds the {@code HELM_*} environment that Helm exports to a plugin subprocess, so a
 * native Helm plugin (helm-diff, helm-s3, …) sees the same configuration it would under
 * {@code helm}. Directory values come from {@link HelmPluginPaths}; the remaining values
 * (namespace, kube context/config, registry/repository config and cache) are supplied by
 * the caller from jhelm's already-resolved runtime configuration.
 *
 * <p>
 * {@link #base()} yields the invocation-wide variables;
 * {@link #forPlugin(DiscoveredHelmPlugin)} additionally adds the per-plugin
 * {@code HELM_PLUGIN_NAME} and {@code HELM_PLUGIN_DIR}. Blank values are omitted rather
 * than exported as empty strings.
 */
@Builder
public class HelmPluginEnvironment {

	private final HelmPluginPaths paths;

	@Builder.Default
	private final String helmBin = "jhelm";

	@Builder.Default
	private final String namespace = "default";

	private final String kubeContext;

	private final String kubeConfig;

	private final String kubeApiServer;

	private final String registryConfig;

	private final String repositoryConfig;

	private final String repositoryCache;

	private final boolean debug;

	/**
	 * The invocation-wide {@code HELM_*} variables shared by every plugin call.
	 * @return an ordered, mutable map of environment variables (blank values omitted)
	 */
	public Map<String, String> base() {
		Map<String, String> env = new LinkedHashMap<>();
		put(env, "HELM_BIN", this.helmBin);
		put(env, "HELM_PLUGINS", str(this.paths.pluginsDir()));
		put(env, "HELM_DATA_HOME", str(this.paths.dataHome()));
		put(env, "HELM_CONFIG_HOME", str(this.paths.configHome()));
		put(env, "HELM_CACHE_HOME", str(this.paths.cacheHome()));
		put(env, "HELM_NAMESPACE", this.namespace);
		put(env, "HELM_KUBECONTEXT", this.kubeContext);
		put(env, "KUBECONFIG", this.kubeConfig);
		put(env, "HELM_KUBEAPISERVER", this.kubeApiServer);
		put(env, "HELM_REGISTRY_CONFIG", this.registryConfig);
		put(env, "HELM_REPOSITORY_CONFIG", this.repositoryConfig);
		put(env, "HELM_REPOSITORY_CACHE", this.repositoryCache);
		put(env, "HELM_DEBUG", Boolean.toString(this.debug));
		return env;
	}

	/**
	 * The full environment for invoking a specific plugin: {@link #base()} plus
	 * {@code HELM_PLUGIN_NAME} and {@code HELM_PLUGIN_DIR}.
	 * @param plugin the plugin about to be invoked
	 * @return an ordered map of environment variables for the plugin process
	 */
	public Map<String, String> forPlugin(DiscoveredHelmPlugin plugin) {
		Map<String, String> env = base();
		put(env, "HELM_PLUGIN_NAME", plugin.name());
		put(env, "HELM_PLUGIN_DIR", str(plugin.directory()));
		return env;
	}

	private static void put(Map<String, String> env, String key, String value) {
		if (value != null && !value.isBlank()) {
			env.put(key, value);
		}
	}

	private static String str(Object value) {
		return (value != null) ? value.toString() : null;
	}

}
