package org.alexmond.jhelm.app.command;

import java.nio.file.Paths;

import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.service.RegistryManager;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.kube.config.JhelmKubernetesProperties;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Implements {@code jhelm env}, printing the environment jhelm runs in — the active
 * namespace, the effective Helm config/registry/cache and kubeconfig locations (resolved
 * the same way jhelm resolves them: explicit property, then
 * {@code HELM_*}/{@code KUBECONFIG} env, then the per-OS Helm default), and the
 * jhelm/Java/OS versions. Mirrors {@code helm env} as a quick way to inspect the client
 * environment.
 */
@Component
@CommandLine.Command(name = "env", mixinStandardHelpOptions = true,
		description = "Print the jhelm client environment information")
public class EnvCommand implements Runnable {

	private final RepoManager repoManager;

	private final RegistryManager registryManager;

	private final JhelmKubernetesProperties kubeProperties;

	/**
	 * Creates the command.
	 * @param repoManager supplies the effective repository config and cache paths
	 * @param registryManager supplies the effective registry config path
	 * @param kubeProperties supplies the configured kubeconfig path override, if any
	 */
	public EnvCommand(RepoManager repoManager, RegistryManager registryManager,
			JhelmKubernetesProperties kubeProperties) {
		this.repoManager = repoManager;
		this.registryManager = registryManager;
		this.kubeProperties = kubeProperties;
	}

	private static String orDefault(String envVar, String fallback) {
		String value = System.getenv(envVar);
		return ((value != null) && !value.isBlank()) ? value : fallback;
	}

	private String resolveKubeconfig() {
		String configured = this.kubeProperties.getKubeconfigPath();
		if ((configured != null) && !configured.isBlank()) {
			return configured;
		}
		return orDefault("KUBECONFIG", Paths.get(System.getProperty("user.home"), ".kube/config").toString());
	}

	@Override
	public void run() {
		CliOutput.println("HELM_NAMESPACE=\"" + orDefault("HELM_NAMESPACE", "default") + "\"");
		CliOutput.println("HELM_REPOSITORY_CONFIG=\"" + this.repoManager.getConfigPath() + "\"");
		CliOutput.println("HELM_REPOSITORY_CACHE=\"" + this.repoManager.getRepositoryCachePath() + "\"");
		CliOutput.println("HELM_REGISTRY_CONFIG=\"" + this.registryManager.getConfigPath() + "\"");
		CliOutput.println("KUBECONFIG=\"" + resolveKubeconfig() + "\"");
		CliOutput.println("HELM_PASSPHRASE_SET=\"" + (System.getenv("HELM_PASSPHRASE") != null) + "\"");
		CliOutput.println("JHELM_VERSION=\"" + VersionCommand.versionString() + "\"");
		CliOutput.println("JAVA_VERSION=\"" + System.getProperty("java.version") + "\"");
		CliOutput.println("OS=\"" + System.getProperty("os.name") + "/" + System.getProperty("os.arch") + "\"");
	}

}
