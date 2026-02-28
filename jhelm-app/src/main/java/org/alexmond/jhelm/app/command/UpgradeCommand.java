package org.alexmond.jhelm.app.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.service.ExternalCommandPostRenderer;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.util.ValuesOverrides;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@Component
@CommandLine.Command(name = "upgrade", mixinStandardHelpOptions = true, description = "Upgrade a release")
@Slf4j
public class UpgradeCommand implements Runnable {

	private final KubeService kubeService;

	private final InstallAction installAction;

	private final UpgradeAction upgradeAction;

	private final ChartLoader chartLoader;

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Parameters(index = "1", description = "chart path")
	private String chartPath;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--install" }, description = "install if not exists")
	private boolean install;

	@CommandLine.Option(names = { "--dry-run" }, description = "simulate an upgrade")
	private boolean dryRun;

	@Option(names = { "-f", "--values" }, description = "specify values YAML files")
	private List<String> valuesFiles = new ArrayList<>();

	@Option(names = { "--set" }, description = "set values on the command line (key=value, dot notation supported)")
	private List<String> setValues = new ArrayList<>();

	@Option(names = { "--wait" }, description = "wait until all resources are ready")
	private boolean wait;

	@Option(names = { "--timeout" }, defaultValue = "300", description = "timeout in seconds for --wait (default 300)")
	private int timeout;

	@Option(names = { "--post-renderer" }, description = "path to an executable to use as a post-renderer")
	private List<String> postRenderers = new ArrayList<>();

	public UpgradeCommand(KubeService kubeService, InstallAction installAction, UpgradeAction upgradeAction,
			ChartLoader chartLoader) {
		this.kubeService = kubeService;
		this.installAction = installAction;
		this.upgradeAction = upgradeAction;
		this.chartLoader = chartLoader;
	}

	@Override
	public void run() {
		try {
			Optional<Release> currentReleaseOpt = kubeService.getRelease(name, namespace);
			Chart chart = chartLoader.load(new File(chartPath));
			Map<String, Object> overrides = ValuesOverrides.parse(valuesFiles, setValues);

			if (currentReleaseOpt.isEmpty()) {
				if (install) {
					Release release = installAction.install(chart, name, namespace, overrides, 1, dryRun);
					applyCliPostRenderers(release);
					if (dryRun) {
						printRelease(release);
					}
					else {
						CliOutput
							.println(CliOutput.success("Release \"" + name + "\" does not exist. Installing it now."));
						if (wait) {
							kubeService.waitForReady(namespace, release.getManifest(), timeout);
						}
					}
				}
				else {
					CliOutput.errPrintln(CliOutput.error("Error: release \"" + name + "\" does not exist"));
				}
				return;
			}

			Release upgradedRelease = upgradeAction.upgrade(currentReleaseOpt.get(), chart, overrides, dryRun);
			applyCliPostRenderers(upgradedRelease);

			if (dryRun) {
				printRelease(upgradedRelease);
			}
			else {
				CliOutput.println(CliOutput.success("Release \"" + name + "\" has been upgraded. Happy Helming!"));
				if (wait) {
					kubeService.waitForReady(namespace, upgradedRelease.getManifest(), timeout);
				}
			}
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error upgrading release: " + ex.getMessage()));
			if (log.isDebugEnabled()) {
				log.debug("Upgrade error details", ex);
			}
		}
	}

	private void applyCliPostRenderers(Release release) throws Exception {
		if (postRenderers.isEmpty()) {
			return;
		}
		String manifest = release.getManifest();
		for (String renderer : postRenderers) {
			manifest = new ExternalCommandPostRenderer(List.of(renderer)).process(manifest);
		}
		release.setManifest(manifest);
	}

	private void printRelease(Release release) {
		CliOutput.println(CliOutput.bold("NAME:") + " " + release.getName());
		CliOutput.println(CliOutput.bold("LAST DEPLOYED:") + " " + release.getInfo().getLastDeployed());
		CliOutput.println(CliOutput.bold("NAMESPACE:") + " " + release.getNamespace());
		CliOutput.println(CliOutput.bold("STATUS:") + " " + release.getInfo().getStatus());
		CliOutput.println(CliOutput.bold("REVISION:") + " " + release.getVersion());
		CliOutput.println("\n" + CliOutput.bold("MANIFEST:") + "\n" + release.getManifest());
	}

}
