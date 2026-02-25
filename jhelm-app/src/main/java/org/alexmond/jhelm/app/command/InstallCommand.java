package org.alexmond.jhelm.app.command;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.service.ChartLoader;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.util.ValuesOverrides;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@CommandLine.Command(name = "install", description = "install a chart")
@Slf4j
public class InstallCommand implements Runnable {

	private final InstallAction installAction;

	private final KubeService kubeService;

	private final ChartLoader chartLoader;

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Parameters(index = "1", description = "chart path")
	private String chartPath;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--dry-run" }, description = "simulate an install")
	private boolean dryRun;

	@Option(names = { "-f", "--values" }, description = "specify values YAML files")
	private List<String> valuesFiles = new ArrayList<>();

	@Option(names = { "--set" }, description = "set values on the command line (key=value, dot notation supported)")
	private List<String> setValues = new ArrayList<>();

	@Option(names = { "--wait" }, description = "wait until all resources are ready")
	private boolean wait;

	@Option(names = { "--timeout" }, defaultValue = "300", description = "timeout in seconds for --wait (default 300)")
	private int timeout;

	public InstallCommand(InstallAction installAction, KubeService kubeService, ChartLoader chartLoader) {
		this.installAction = installAction;
		this.kubeService = kubeService;
		this.chartLoader = chartLoader;
	}

	@Override
	public void run() {
		try {
			Chart chart = chartLoader.load(new File(chartPath));
			Map<String, Object> overrides = ValuesOverrides.parse(valuesFiles, setValues);

			Release release = installAction.install(chart, name, namespace, overrides, 1, dryRun);

			if (dryRun) {
				log.info("NAME: {}", release.getName());
				log.info("LAST DEPLOYED: {}", release.getInfo().getLastDeployed());
				log.info("NAMESPACE: {}", release.getNamespace());
				log.info("STATUS: {}", release.getInfo().getStatus());
				log.info("REVISION: {}", release.getVersion());
				log.info("\nMANIFEST:\n{}", release.getManifest());
			}
			else {
				log.info("Release \"{}\" has been installed.", name);
				if (wait) {
					kubeService.waitForReady(namespace, release.getManifest(), timeout);
				}
			}
		}
		catch (Exception ex) {
			log.error("Error installing chart: {}", ex.getMessage(), ex);
		}
	}

}
