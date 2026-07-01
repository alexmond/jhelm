package org.alexmond.jhelm.app.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.ExternalCommandPostRenderer;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.util.ValuesOverrides;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * Implements {@code jhelm install RELEASE CHART}, installing a chart into the cluster.
 * Supports values overrides, dry-run, waiting for readiness, atomic rollback on failure,
 * and post-renderers.
 */
@Component
@CommandLine.Command(name = "install", mixinStandardHelpOptions = true, description = "Install a chart")
@Slf4j
public class InstallCommand implements Runnable {

	private final InstallAction installAction;

	private final UninstallAction uninstallAction;

	private final KubeService kubeService;

	private final ChartResolver chartResolver;

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Parameters(index = "1", description = "chart path")
	private String chartPath;

	@Option(names = { "--verify" }, description = "verify the packaged chart's provenance before installing")
	private boolean verify;

	@Option(names = { "--keyring" }, description = "path to the PGP public keyring file (for --verify)")
	private String keyring;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--dry-run" }, description = "simulate an install")
	private boolean dryRun;

	@Option(names = { "-f", "--values" }, description = "specify values YAML files")
	private List<String> valuesFiles = new ArrayList<>();

	@Option(names = { "--set" }, description = "set values on the command line (key=value, dot notation supported)")
	private List<String> setValues = new ArrayList<>();

	@Option(names = { "--set-string" }, description = "set STRING values on the command line (no type coercion)")
	private List<String> setStringValues = new ArrayList<>();

	@Option(names = { "--set-file" }, description = "set values from files (key=path), value is the file contents")
	private List<String> setFileValues = new ArrayList<>();

	@Option(names = { "--set-json" }, description = "set JSON values on the command line (key=json)")
	private List<String> setJsonValues = new ArrayList<>();

	@Option(names = { "--wait" }, description = "wait until all resources are ready")
	private boolean wait;

	@Option(names = { "--timeout" }, defaultValue = "300", description = "timeout in seconds for --wait (default 300)")
	private int timeout;

	@Option(names = { "--atomic" }, description = "uninstall on failure (implies --wait)")
	private boolean atomic;

	@Option(names = { "--post-renderer" }, description = "path to an executable to use as a post-renderer")
	private List<String> postRenderers = new ArrayList<>();

	@Option(names = { "--no-hooks" }, description = "prevent hooks from running during this operation")
	private boolean noHooks;

	@CommandLine.Option(names = { "--create-namespace" }, description = "create the release namespace if not present")
	private boolean createNamespace;

	/**
	 * Creates the command.
	 * @param installAction the action that performs the install
	 * @param uninstallAction the action used to roll back when {@code --atomic} is set
	 * @param kubeService the Kubernetes service used to wait for resource readiness
	 * @param chartResolver resolves the chart source (directory or {@code .tgz}), with
	 * optional provenance verification
	 */
	public InstallCommand(InstallAction installAction, UninstallAction uninstallAction, KubeService kubeService,
			ChartResolver chartResolver) {
		this.installAction = installAction;
		this.uninstallAction = uninstallAction;
		this.kubeService = kubeService;
		this.chartResolver = chartResolver;
	}

	@Override
	public void run() {
		try {
			Chart chart = chartResolver.resolve(chartPath, verify, keyring);
			Map<String, Object> overrides = ValuesOverrides.parse(valuesFiles, setValues, setStringValues,
					setFileValues, setJsonValues);

			Release release = installAction.install(InstallOptions.builder()
				.chart(chart)
				.releaseName(name)
				.namespace(namespace)
				.values(overrides)
				.revision(1)
				.dryRun(dryRun)
				.noHooks(noHooks)
				.createNamespace(createNamespace)
				.build());
			release = applyCliPostRenderers(release);

			if (dryRun) {
				CliOutput.println(CliOutput.bold("NAME:") + " " + release.getName());
				CliOutput.println(CliOutput.bold("LAST DEPLOYED:") + " " + release.getInfo().getLastDeployed());
				CliOutput.println(CliOutput.bold("NAMESPACE:") + " " + release.getNamespace());
				CliOutput.println(CliOutput.bold("STATUS:") + " " + release.getInfo().getStatus().getValue());
				CliOutput.println(CliOutput.bold("REVISION:") + " " + release.getVersion());
				CliOutput.println("\n" + CliOutput.bold("MANIFEST:") + "\n" + release.getManifest());
			}
			else {
				CliOutput.println(CliOutput.success("Release \"" + name + "\" has been installed."));
				if (wait || atomic) {
					kubeService.waitForReady(namespace, release.getManifest(), timeout);
				}
			}
		}
		catch (Exception ex) {
			if (atomic) {
				CliOutput
					.errPrintln(CliOutput.error("Install failed, performing atomic uninstall: " + ex.getMessage()));
				try {
					uninstallAction
						.uninstall(UninstallOptions.builder().releaseName(name).namespace(namespace).build());
					CliOutput.println("Atomic uninstall of \"" + name + "\" complete.");
				}
				catch (Exception rollbackEx) {
					CliOutput.errPrintln(CliOutput.error("Atomic uninstall failed: " + rollbackEx.getMessage()));
				}
			}
			else {
				CliOutput.errPrintln(CliOutput.error("Error installing chart: " + ex.getMessage()));
			}
			if (log.isDebugEnabled()) {
				log.debug("Install error details", ex);
			}
		}
	}

	private Release applyCliPostRenderers(Release release) throws Exception {
		if (postRenderers.isEmpty()) {
			return release;
		}
		String manifest = release.getManifest();
		for (String renderer : postRenderers) {
			manifest = new ExternalCommandPostRenderer(List.of(renderer)).process(manifest);
		}
		return release.toBuilder().manifest(manifest).build();
	}

}
