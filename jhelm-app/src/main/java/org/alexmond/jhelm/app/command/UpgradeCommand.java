package org.alexmond.jhelm.app.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
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
@CommandLine.Command(name = "upgrade", description = "upgrade a release")
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
						log.info("Release \"{}\" does not exist. Installing it now.", name);
						if (wait) {
							kubeService.waitForReady(namespace, release.getManifest(), timeout);
						}
					}
				}
				else {
					log.error("Error: release \"{}\" does not exist", name);
				}
				return;
			}

			Release upgradedRelease = upgradeAction.upgrade(currentReleaseOpt.get(), chart, overrides, dryRun);
			applyCliPostRenderers(upgradedRelease);

			if (dryRun) {
				printRelease(upgradedRelease);
			}
			else {
				log.info("Release \"{}\" has been upgraded. Happy Helming!", name);
				if (wait) {
					kubeService.waitForReady(namespace, upgradedRelease.getManifest(), timeout);
				}
			}
		}
		catch (Exception ex) {
			log.error("Error upgrading release: {}", ex.getMessage(), ex);
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
		log.info("NAME: {}", release.getName());
		log.info("LAST DEPLOYED: {}", release.getInfo().getLastDeployed());
		log.info("NAMESPACE: {}", release.getNamespace());
		log.info("STATUS: {}", release.getInfo().getStatus());
		log.info("REVISION: {}", release.getVersion());
		log.info("\nMANIFEST:\n{}", release.getManifest());
	}

}
