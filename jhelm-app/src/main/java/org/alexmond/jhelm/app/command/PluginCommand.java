package org.alexmond.jhelm.app.command;

import java.io.File;
import java.util.List;

import org.alexmond.jhelm.plugin.model.PluginDescriptor;
import org.alexmond.jhelm.plugin.service.PluginManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "plugin", description = "Manage plugins",
		subcommands = { PluginCommand.Install.class, PluginCommand.Uninstall.class, PluginCommand.ListPlugins.class })
public class PluginCommand implements Runnable {

	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

	@Component
	@CommandLine.Command(name = "install", description = "Install a plugin from a .jhp archive")
	public static class Install implements Runnable {

		@CommandLine.Parameters(index = "0", description = "Path to plugin archive (.jhp)")
		private File archivePath;

		private final ObjectProvider<PluginManager> pluginManagerProvider;

		public Install(ObjectProvider<PluginManager> pluginManagerProvider) {
			this.pluginManagerProvider = pluginManagerProvider;
		}

		@Override
		public void run() {
			PluginManager pm = pluginManagerProvider.getIfAvailable();
			if (pm == null) {
				System.err.println("Plugin system is not enabled. Set jhelm.plugins.enabled=true");
				return;
			}
			try {
				PluginDescriptor desc = pm.install(archivePath);
				System.out.println("Installed plugin: " + desc.getManifest().getName() + " (type: "
						+ desc.getManifest().getType().getValue() + ")");
			}
			catch (Exception ex) {
				System.err.println("Failed to install plugin: " + ex.getMessage());
			}
		}

	}

	@Component
	@CommandLine.Command(name = "uninstall", description = "Uninstall a plugin")
	public static class Uninstall implements Runnable {

		@CommandLine.Parameters(index = "0", description = "Plugin name")
		private String pluginName;

		private final ObjectProvider<PluginManager> pluginManagerProvider;

		public Uninstall(ObjectProvider<PluginManager> pluginManagerProvider) {
			this.pluginManagerProvider = pluginManagerProvider;
		}

		@Override
		public void run() {
			PluginManager pm = pluginManagerProvider.getIfAvailable();
			if (pm == null) {
				System.err.println("Plugin system is not enabled. Set jhelm.plugins.enabled=true");
				return;
			}
			try {
				pm.uninstall(pluginName);
				System.out.println("Uninstalled plugin: " + pluginName);
			}
			catch (Exception ex) {
				System.err.println("Failed to uninstall plugin: " + ex.getMessage());
			}
		}

	}

	@Component
	@CommandLine.Command(name = "list", description = "List installed plugins")
	public static class ListPlugins implements Runnable {

		private final ObjectProvider<PluginManager> pluginManagerProvider;

		public ListPlugins(ObjectProvider<PluginManager> pluginManagerProvider) {
			this.pluginManagerProvider = pluginManagerProvider;
		}

		@Override
		public void run() {
			PluginManager pm = pluginManagerProvider.getIfAvailable();
			if (pm == null) {
				System.err.println("Plugin system is not enabled. Set jhelm.plugins.enabled=true");
				return;
			}
			List<PluginDescriptor> plugins = pm.list();
			if (plugins.isEmpty()) {
				System.out.println("No plugins installed.");
				return;
			}
			System.out.printf("%-20s %-15s %-10s%n", "NAME", "TYPE", "VERSION");
			for (PluginDescriptor desc : plugins) {
				System.out.printf("%-20s %-15s %-10s%n", desc.getManifest().getName(),
						desc.getManifest().getType().getValue(), desc.getManifest().getVersion());
			}
		}

	}

}
