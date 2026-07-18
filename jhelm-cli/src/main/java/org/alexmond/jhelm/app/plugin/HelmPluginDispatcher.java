package org.alexmond.jhelm.app.plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.service.RegistryManager;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.kube.config.JhelmKubernetesProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ExitCode;

/**
 * Resolves and runs installed Helm subcommand plugins (helm-diff, helm-secrets, …). When
 * {@code jhelm <name>} names an installed Helm plugin rather than a built-in command, the
 * dispatcher executes the plugin's {@code command} for the current platform as a child
 * process, forwarding the remaining arguments and the {@code HELM_*} environment, so
 * existing Helm plugin workflows run unchanged against jhelm.
 *
 * <p>
 * Running a native plugin is gated on {@link HelmPluginExecGuard}: it proceeds only in
 * the CLI's {@code FULL} security posture (the plugin is arbitrary local code, the same
 * trust boundary as {@code helm}).
 */
@Slf4j
@Component
public class HelmPluginDispatcher {

	private final HelmPluginDiscovery discovery;

	private final Supplier<HelmPluginEnvironment> environment;

	private final JhelmSecurityPolicy policy;

	private final HelmPluginRunner runner;

	/**
	 * Spring constructor: builds the discovery and {@code HELM_*} environment from
	 * jhelm's already-resolved runtime configuration.
	 * @param repoManager supplies the repository config and cache paths
	 * @param registryManager supplies the registry config path
	 * @param kubeProperties supplies the configured kubeconfig override, if any
	 * @param policy the security policy that gates native plugin execution
	 * @param runner runs the resolved plugin command as a child process
	 */
	@Autowired
	public HelmPluginDispatcher(RepoManager repoManager, RegistryManager registryManager,
			JhelmKubernetesProperties kubeProperties, JhelmSecurityPolicy policy, HelmPluginRunner runner) {
		this(new HelmPluginDiscovery(HelmPluginPaths.fromEnvironment()),
				() -> springEnvironment(repoManager, registryManager, kubeProperties), policy, runner);
	}

	HelmPluginDispatcher(HelmPluginDiscovery discovery, Supplier<HelmPluginEnvironment> environment,
			JhelmSecurityPolicy policy, HelmPluginRunner runner) {
		this.discovery = discovery;
		this.environment = environment;
		this.policy = policy;
		this.runner = runner;
	}

	/**
	 * Looks up an installed Helm plugin by the command name.
	 * @param name the candidate command name ({@code jhelm <name>})
	 * @return the matching installed plugin, or empty if none is installed under that
	 * name
	 */
	public Optional<DiscoveredHelmPlugin> find(String name) {
		return this.discovery.find(name);
	}

	/**
	 * Runs a plugin with the given arguments, forwarding the {@code HELM_*} environment
	 * and returning the plugin's exit code. Refuses (and returns a non-zero code) when
	 * the security posture disallows native execution or the plugin declares no command
	 * for the current platform.
	 * @param plugin the plugin to run
	 * @param args the arguments to pass after the plugin command
	 * @return the plugin's exit code, or a non-zero jhelm exit code on refusal/error
	 */
	public int dispatch(DiscoveredHelmPlugin plugin, List<String> args) {
		if (HelmPluginExecGuard.blocked(this.policy)) {
			return ExitCode.SOFTWARE;
		}
		Map<String, String> env = this.environment.get().forPlugin(plugin);
		List<String> command = plugin.resolveCommand(HelmPlatform.currentOs(), HelmPlatform.currentArch(), env);
		if (command.isEmpty()) {
			CliOutput
				.errPrintln(CliOutput.error("plugin '" + plugin.name() + "' declares no command for this platform ("
						+ HelmPlatform.currentOs() + "/" + HelmPlatform.currentArch() + ')'));
			return ExitCode.SOFTWARE;
		}
		List<String> full = new ArrayList<>(command);
		full.addAll(args);
		try {
			return this.runner.run(full, env, plugin.directory());
		}
		catch (IOException ex) {
			CliOutput.errPrintln(CliOutput.error("failed to run plugin '" + plugin.name() + "': " + ex.getMessage()));
			return ExitCode.SOFTWARE;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			CliOutput.errPrintln(CliOutput.error("interrupted while running plugin '" + plugin.name() + '\''));
			return ExitCode.SOFTWARE;
		}
	}

	private static HelmPluginEnvironment springEnvironment(RepoManager repoManager, RegistryManager registryManager,
			JhelmKubernetesProperties kubeProperties) {
		return HelmPluginEnvironment.builder()
			.paths(HelmPluginPaths.fromEnvironment())
			.namespace(envOrDefault("HELM_NAMESPACE", "default"))
			.kubeConfig(resolveKubeconfig(kubeProperties))
			.registryConfig(registryManager.getConfigPath())
			.repositoryConfig(repoManager.getConfigPath())
			.repositoryCache(repoManager.getRepositoryCachePath())
			.debug(Boolean.parseBoolean(envOrDefault("HELM_DEBUG", "false")))
			.build();
	}

	private static String resolveKubeconfig(JhelmKubernetesProperties kubeProperties) {
		String configured = kubeProperties.getKubeconfigPath();
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

	// Retained for symmetry with test construction over an explicit plugins directory.
	static HelmPluginDispatcher forTesting(Path pluginsDir, Supplier<HelmPluginEnvironment> environment,
			JhelmSecurityPolicy policy, HelmPluginRunner runner) {
		HelmPluginPaths paths = new HelmPluginPaths(Map.of("HELM_PLUGINS", pluginsDir.toString())::get,
				Path.of(System.getProperty("user.home", ".")));
		return new HelmPluginDispatcher(new HelmPluginDiscovery(paths), environment, policy, runner);
	}

}
