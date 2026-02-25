package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.Chart;
import org.alexmond.jhelm.core.ChartLoader;
import org.alexmond.jhelm.core.Release;
import org.alexmond.jhelm.core.KubeService;
import org.alexmond.jhelm.core.InstallAction;
import org.alexmond.jhelm.core.UpgradeAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import org.alexmond.jhelm.core.ValuesOverrides;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@CommandLine.Command(name = "upgrade", description = "upgrade a release")
@Slf4j
public class UpgradeCommand implements Runnable {

	private final KubeService kubeService;

	private final InstallAction installAction;

	private final UpgradeAction upgradeAction;

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

	public UpgradeCommand(KubeService kubeService, InstallAction installAction, UpgradeAction upgradeAction) {
		this.kubeService = kubeService;
		this.installAction = installAction;
		this.upgradeAction = upgradeAction;
	}

	@Override
	public void run() {
		try {
			Optional<Release> currentReleaseOpt = kubeService.getRelease(name, namespace);
			ChartLoader loader = new ChartLoader();
			Chart chart = loader.load(new File(chartPath));
			Map<String, Object> overrides = ValuesOverrides.parse(valuesFiles, setValues);

			if (currentReleaseOpt.isEmpty()) {
				if (install) {
					Release release = installAction.install(chart, name, namespace, overrides, 1, dryRun);
					if (dryRun) {
						printRelease(release);
					}
					else {
						log.info("Release \"{}\" does not exist. Installing it now.", name);
					}
				}
				else {
					log.error("Error: release \"{}\" does not exist", name);
				}
				return;
			}

			Release upgradedRelease = upgradeAction.upgrade(currentReleaseOpt.get(), chart, overrides, dryRun);

			if (dryRun) {
				printRelease(upgradedRelease);
			}
			else {
				log.info("Release \"{}\" has been upgraded. Happy Helming!", name);
			}
		}
		catch (Exception ex) {
			log.error("Error upgrading release: {}", ex.getMessage(), ex);
		}
	}

	private void printRelease(Release release) {
		log.info("NAME: {}", release.getName());
		log.info("LAST DEPLOYED: {}", release.getInfo().getLastDeployed());
		log.info("NAMESPACE: {}", release.getNamespace());
		log.info("STATUS: {}", release.getInfo().getStatus());
		log.info("REVISION: {}", release.getVersion());
		log.info("\nMANIFEST:\n{}", release.getManifest());
	}

}
