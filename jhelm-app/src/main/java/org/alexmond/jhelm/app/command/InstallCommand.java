package org.alexmond.jhelm.app.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.InstallAction;
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
@CommandLine.Command(name = "install", mixinStandardHelpOptions = true, description = "Install a chart")
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

	@Option(names = { "--post-renderer" }, description = "path to an executable to use as a post-renderer")
	private List<String> postRenderers = new ArrayList<>();

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
			applyCliPostRenderers(release);

			if (dryRun) {
				CliOutput.println(CliOutput.bold("NAME:") + " " + release.getName());
				CliOutput.println(CliOutput.bold("LAST DEPLOYED:") + " " + release.getInfo().getLastDeployed());
				CliOutput.println(CliOutput.bold("NAMESPACE:") + " " + release.getNamespace());
				CliOutput.println(CliOutput.bold("STATUS:") + " " + release.getInfo().getStatus());
				CliOutput.println(CliOutput.bold("REVISION:") + " " + release.getVersion());
				CliOutput.println("\n" + CliOutput.bold("MANIFEST:") + "\n" + release.getManifest());
			}
			else {
				CliOutput.println(CliOutput.success("Release \"" + name + "\" has been installed."));
				if (wait) {
					kubeService.waitForReady(namespace, release.getManifest(), timeout);
				}
			}
		}
		catch (Exception ex) {
			CliOutput.errPrintln(CliOutput.error("Error installing chart: " + ex.getMessage()));
			log.debug("Install error details", ex);
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

}
