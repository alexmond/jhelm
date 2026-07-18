package org.alexmond.jhelm.app.plugin;

import java.nio.file.Paths;

import org.alexmond.jhelm.core.service.RegistryManager;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.kube.config.JhelmKubernetesProperties;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code HELM_*} environment exported to Helm plugin processes (subcommand
 * dispatch and install/update/delete hooks) from jhelm's already-resolved runtime
 * configuration — the same sources {@code jhelm env} reports. Centralized here so the
 * dispatcher and the installer export an identical environment.
 */
@Component
public class HelmPluginEnvironmentFactory {

	private final RepoManager repoManager;

	private final RegistryManager registryManager;

	private final JhelmKubernetesProperties kubeProperties;

	/**
	 * Creates the factory.
	 * @param repoManager supplies the repository config and cache paths
	 * @param registryManager supplies the registry config path
	 * @param kubeProperties supplies the configured kubeconfig override, if any
	 */
	public HelmPluginEnvironmentFactory(RepoManager repoManager, RegistryManager registryManager,
			JhelmKubernetesProperties kubeProperties) {
		this.repoManager = repoManager;
		this.registryManager = registryManager;
		this.kubeProperties = kubeProperties;
	}

	/**
	 * Builds a {@link HelmPluginEnvironment} from the current runtime configuration.
	 * @return the environment source for plugin invocations
	 */
	public HelmPluginEnvironment create() {
		return HelmPluginEnvironment.builder()
			.paths(HelmPluginPaths.fromEnvironment())
			.namespace(envOrDefault("HELM_NAMESPACE", "default"))
			.kubeConfig(resolveKubeconfig())
			.registryConfig(this.registryManager.getConfigPath())
			.repositoryConfig(this.repoManager.getConfigPath())
			.repositoryCache(this.repoManager.getRepositoryCachePath())
			.debug(Boolean.parseBoolean(envOrDefault("HELM_DEBUG", "false")))
			.build();
	}

	private String resolveKubeconfig() {
		String configured = this.kubeProperties.getKubeconfigPath();
		if (configured != null && !configured.isBlank()) {
			return configured;
		}
		return envOrDefault("KUBECONFIG",
				Paths.get(System.getProperty("user.home", "."), ".kube", "config").toString());
	}

	private static String envOrDefault(String name, String fallback) {
		String value = System.getenv(name);
		return (value != null && !value.isBlank()) ? value : fallback;
	}

}
