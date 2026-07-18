package org.alexmond.jhelm.app.command;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import org.alexmond.jhelm.app.plugin.DiscoveredHelmPlugin;
import org.alexmond.jhelm.app.plugin.HelmPluginInstaller;
import org.alexmond.jhelm.plugin.model.PluginDescriptor;
import org.alexmond.jhelm.plugin.service.PluginManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import org.alexmond.jhelm.app.output.CliOutput;

/**
 * Implements {@code jhelm plugin}, managing installed plugins via the {@code install},
 * {@code uninstall}, and {@code list} subcommands.
 */
@Component
@CommandLine.Command(name = "plugin", mixinStandardHelpOptions = true, description = "Manage plugins",
		subcommands = { PluginCommand.Install.class, PluginCommand.Uninstall.class, PluginCommand.Update.class,
				PluginCommand.ListPlugins.class })
public class PluginCommand implements Callable<Integer> {

	/** Creates the command. */
	@SuppressWarnings("PMD.UnnecessaryConstructor")
	public PluginCommand() {
	}

	/**
	 * Prints the usage help when {@code plugin} is invoked without a subcommand.
	 */
	@Override
	public Integer call() {
		CommandLine.usage(this, System.out);
		return CommandLine.ExitCode.OK;
	}

	/**
	 * Implements {@code plugin install}: installs a Helm plugin from a git URL, a
	 * {@code .tar.gz}/{@code .tgz} archive, or a local directory (mirroring
	 * {@code helm plugin install}), or a jhelm {@code .jhp} WASM archive.
	 */
	@Component
	@CommandLine.Command(name = "install", mixinStandardHelpOptions = true,
			description = "Install a plugin from a git URL, a .tar.gz/.tgz, a directory, or a .jhp archive")
	public static class Install implements Callable<Integer> {

		@CommandLine.Parameters(index = "0",
				description = "Plugin source: a git URL, a .tar.gz/.tgz path or URL, a directory, or a .jhp archive")
		private String source;

		@CommandLine.Option(names = { "--version" },
				description = "git ref (tag, branch, or commit) to install for a git source")
		private String version;

		private final ObjectProvider<PluginManager> pluginManagerProvider;

		private final HelmPluginInstaller helmInstaller;

		/**
		 * Creates the command.
		 * @param pluginManagerProvider provider for the WASM plugin manager (may be
		 * absent when the {@code .jhp} plugin system is disabled)
		 * @param helmInstaller installs Helm plugins from a git URL, archive, or
		 * directory
		 */
		public Install(ObjectProvider<PluginManager> pluginManagerProvider, HelmPluginInstaller helmInstaller) {
			this.pluginManagerProvider = pluginManagerProvider;
			this.helmInstaller = helmInstaller;
		}

		@Override
		public Integer call() {
			if (this.source.toLowerCase(Locale.ROOT).endsWith(".jhp")) {
				return installWasm();
			}
			try {
				DiscoveredHelmPlugin plugin = this.helmInstaller.install(this.source, this.version);
				String ver = plugin.manifest().getVersion();
				CliOutput.println(CliOutput.success(
						"Installed plugin: " + plugin.name() + ((ver != null && !ver.isBlank()) ? " " + ver : "")));
				return CommandLine.ExitCode.OK;
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				CliOutput.errPrintln(CliOutput.error("Interrupted while installing plugin"));
				return CommandLine.ExitCode.SOFTWARE;
			}
			catch (IOException ex) {
				CliOutput.errPrintln(CliOutput.error("Failed to install plugin: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

		private Integer installWasm() {
			PluginManager pm = this.pluginManagerProvider.getIfAvailable();
			if (pm == null) {
				CliOutput.errPrintln(CliOutput.error("Plugin system is not enabled. Set jhelm.plugins.enabled=true"));
				return CommandLine.ExitCode.SOFTWARE;
			}
			try {
				PluginDescriptor desc = pm.install(new File(this.source));
				CliOutput.println(CliOutput.success("Installed plugin: " + desc.getManifest().getName() + " (type: "
						+ desc.getManifest().getType().getValue() + ")"));
				return CommandLine.ExitCode.OK;
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Failed to install plugin: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

	/** Implements {@code plugin uninstall}: removes an installed plugin by name. */
	@Component
	@CommandLine.Command(name = "uninstall", mixinStandardHelpOptions = true, description = "Uninstall a plugin")
	public static class Uninstall implements Callable<Integer> {

		@CommandLine.Parameters(index = "0", description = "Plugin name")
		private String pluginName;

		private final ObjectProvider<PluginManager> pluginManagerProvider;

		private final HelmPluginInstaller helmInstaller;

		/**
		 * Creates the command.
		 * @param pluginManagerProvider provider for the WASM plugin manager (may be
		 * absent when the {@code .jhp} plugin system is disabled)
		 * @param helmInstaller uninstalls Helm plugins from the Helm plugins directory
		 */
		public Uninstall(ObjectProvider<PluginManager> pluginManagerProvider, HelmPluginInstaller helmInstaller) {
			this.pluginManagerProvider = pluginManagerProvider;
			this.helmInstaller = helmInstaller;
		}

		@Override
		public Integer call() {
			try {
				if (this.helmInstaller.uninstall(this.pluginName)) {
					CliOutput.println(CliOutput.success("Uninstalled plugin: " + this.pluginName));
					return CommandLine.ExitCode.OK;
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				CliOutput.errPrintln(CliOutput.error("Interrupted while uninstalling plugin"));
				return CommandLine.ExitCode.SOFTWARE;
			}
			catch (IOException ex) {
				CliOutput.errPrintln(CliOutput.error("Failed to uninstall plugin: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
			return uninstallWasm();
		}

		private Integer uninstallWasm() {
			PluginManager pm = this.pluginManagerProvider.getIfAvailable();
			if (pm == null) {
				CliOutput.errPrintln(CliOutput.error("Plugin '" + this.pluginName + "' is not installed"));
				return CommandLine.ExitCode.SOFTWARE;
			}
			try {
				pm.uninstall(this.pluginName);
				CliOutput.println(CliOutput.success("Uninstalled plugin: " + this.pluginName));
				return CommandLine.ExitCode.OK;
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Failed to uninstall plugin: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

	/** Implements {@code plugin update}: refreshes an installed Helm plugin by name. */
	@Component
	@CommandLine.Command(name = "update", mixinStandardHelpOptions = true,
			description = "Update an installed Helm plugin (git pull + update hook)")
	public static class Update implements Callable<Integer> {

		@CommandLine.Parameters(index = "0", description = "Plugin name")
		private String pluginName;

		private final HelmPluginInstaller helmInstaller;

		/**
		 * Creates the command.
		 * @param helmInstaller updates Helm plugins in the Helm plugins directory
		 */
		public Update(HelmPluginInstaller helmInstaller) {
			this.helmInstaller = helmInstaller;
		}

		@Override
		public Integer call() {
			try {
				if (this.helmInstaller.update(this.pluginName).isEmpty()) {
					CliOutput.errPrintln(CliOutput.error("Plugin '" + this.pluginName + "' is not installed"));
					return CommandLine.ExitCode.SOFTWARE;
				}
				CliOutput.println(CliOutput.success("Updated plugin: " + this.pluginName));
				return CommandLine.ExitCode.OK;
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				CliOutput.errPrintln(CliOutput.error("Interrupted while updating plugin"));
				return CommandLine.ExitCode.SOFTWARE;
			}
			catch (IOException ex) {
				CliOutput.errPrintln(CliOutput.error("Failed to update plugin: " + ex.getMessage()));
				return CommandLine.ExitCode.SOFTWARE;
			}
		}

	}

	/** Implements {@code plugin list}: prints the installed Helm and WASM plugins. */
	@Component
	@CommandLine.Command(name = "list", mixinStandardHelpOptions = true, description = "List installed plugins")
	public static class ListPlugins implements Callable<Integer> {

		private final ObjectProvider<PluginManager> pluginManagerProvider;

		private final HelmPluginInstaller helmInstaller;

		/**
		 * Creates the command.
		 * @param pluginManagerProvider provider for the WASM plugin manager (may be
		 * absent when the {@code .jhp} plugin system is disabled)
		 * @param helmInstaller lists Helm plugins from the Helm plugins directory
		 */
		public ListPlugins(ObjectProvider<PluginManager> pluginManagerProvider, HelmPluginInstaller helmInstaller) {
			this.pluginManagerProvider = pluginManagerProvider;
			this.helmInstaller = helmInstaller;
		}

		@Override
		public Integer call() {
			List<DiscoveredHelmPlugin> helmPlugins = this.helmInstaller.list();
			PluginManager pm = this.pluginManagerProvider.getIfAvailable();
			List<PluginDescriptor> wasmPlugins = (pm != null) ? pm.list() : List.of();
			if (helmPlugins.isEmpty() && wasmPlugins.isEmpty()) {
				CliOutput.println("No plugins installed.");
				return CommandLine.ExitCode.OK;
			}
			CliOutput.printf("%-24s %-6s %-12s %s%n", CliOutput.bold("NAME"), CliOutput.bold("KIND"),
					CliOutput.bold("VERSION"), CliOutput.bold("DESCRIPTION"));
			for (DiscoveredHelmPlugin plugin : helmPlugins) {
				CliOutput.printf("%-24s %-6s %-12s %s%n", plugin.name(), "helm", orDash(plugin.manifest().getVersion()),
						orDash(plugin.manifest().getUsage()));
			}
			for (PluginDescriptor desc : wasmPlugins) {
				CliOutput.printf("%-24s %-6s %-12s %s%n", desc.getManifest().getName(), "wasm",
						orDash(desc.getManifest().getVersion()), orDash(desc.getManifest().getDescription()));
			}
			return CommandLine.ExitCode.OK;
		}

		private static String orDash(String value) {
			return (value != null && !value.isBlank()) ? value : "-";
		}

	}

}
