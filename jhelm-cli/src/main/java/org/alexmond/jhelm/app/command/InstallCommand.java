package org.alexmond.jhelm.app.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.output.OutputFormat;
import org.alexmond.jhelm.core.action.DependencyUpdateAction;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
import org.alexmond.jhelm.core.config.JhelmCoreProperties;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.ConfigServerValuesLoader;
import org.alexmond.jhelm.core.service.ExternalCommandPostRenderer;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.util.ReleaseNames;
import org.alexmond.jhelm.core.util.ValuesOverrides;
import org.alexmond.jhelm.core.util.ValuesProfiles;
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
public class InstallCommand implements Callable<Integer> {

	private final InstallAction installAction;

	private final UninstallAction uninstallAction;

	private final KubeService kubeService;

	private final ChartResolver chartResolver;

	private final JhelmSecurityPolicy securityPolicy;

	private final JhelmCoreProperties coreProperties;

	private final ConfigServerValuesLoader configServerValuesLoader;

	private final DependencyUpdateAction dependencyUpdateAction;

	@CommandLine.Mixin
	private final ConfigServerCliOptions configServerOptions = new ConfigServerCliOptions();

	@CommandLine.Mixin
	private final RepoChartOptions repoOptions = new RepoChartOptions();

	@CommandLine.Parameters(arity = "1..2", paramLabel = "[NAME] CHART",
			description = "release name (optional with -g/--generate-name) followed by the chart path")
	private List<String> nameAndChart = new ArrayList<>();

	// Resolved from nameAndChart at the start of call(): the release name (or a generated
	// one)
	// and the chart path.
	private String name;

	private String chartPath;

	@Option(names = { "-g", "--generate-name" },
			description = "generate the release name (as <chart>-<timestamp>); the NAME positional is then omitted")
	private boolean generateName;

	@Option(names = { "--verify" }, description = "verify the packaged chart's provenance before installing")
	private boolean verify;

	@Option(names = { "--keyring" }, description = "path to the PGP public keyring file (for --verify)")
	private String keyring;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--dry-run" }, arity = "0..1", fallbackValue = "client", paramLabel = "MODE",
			description = "simulate an install without installing: client (default), server, or none")
	private String dryRun;

	private boolean serverDryRun;

	@Option(names = { "-P", "--profile" }, split = ",",
			description = "active value profile(s): gate spring.config.activate.on-profile documents and select "
					+ "values-<profile>.yaml sidecars (comma-separated or repeatable)")
	private List<String> profileNames = new ArrayList<>();

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

	@Option(names = { "--set-literal" },
			description = "set a literal STRING value on the command line (key=value, no coercion or escaping)")
	private List<String> setLiteralValues = new ArrayList<>();

	@Option(names = { "--wait" }, description = "wait until all resources are ready")
	private boolean wait;

	@Option(names = { "--wait-for-jobs" },
			description = "wait for Jobs to complete before marking deployed (implies --wait; "
					+ "jhelm's --wait already waits for Jobs)")
	private boolean waitForJobs;

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

	@Option(names = { "--description" }, description = "custom release description (stored on the release)")
	private String description;

	@Option(names = { "--labels" }, split = ",",
			description = "labels to add to the release Secret (key=value, comma-separated or repeatable)")
	private Map<String, String> labels = new LinkedHashMap<>();

	@CommandLine.Option(names = { "-o", "--output" }, defaultValue = "table",
			description = "output format: table (default human summary), json, or yaml")
	private String output;

	@Option(names = { "--dependency-update" },
			description = "update dependencies from Chart.yaml to charts/ before installing (local chart dir only)")
	private boolean dependencyUpdate;

	/**
	 * Creates the command.
	 * @param installAction the action that performs the install
	 * @param uninstallAction the action used to roll back when {@code --atomic} is set
	 * @param kubeService the Kubernetes service used to wait for resource readiness
	 * @param chartResolver resolves the chart source (directory or {@code .tgz}), with
	 * optional provenance verification
	 * @param securityPolicy the unified access-mode policy; install is refused unless
	 * mutating operations are enabled
	 * @param coreProperties the core properties supplying default active profiles
	 * @param configServerValuesLoader loads values from a Spring Cloud Config Server
	 * @param dependencyUpdateAction updates chart dependencies when
	 * {@code --dependency-update} is set
	 */
	public InstallCommand(InstallAction installAction, UninstallAction uninstallAction, KubeService kubeService,
			ChartResolver chartResolver, JhelmSecurityPolicy securityPolicy, JhelmCoreProperties coreProperties,
			ConfigServerValuesLoader configServerValuesLoader, DependencyUpdateAction dependencyUpdateAction) {
		this.installAction = installAction;
		this.uninstallAction = uninstallAction;
		this.kubeService = kubeService;
		this.chartResolver = chartResolver;
		this.securityPolicy = securityPolicy;
		this.coreProperties = coreProperties;
		this.configServerValuesLoader = configServerValuesLoader;
		this.dependencyUpdateAction = dependencyUpdateAction;
	}

	@Override
	public Integer call() {
		if (MutatingGuard.blocked(securityPolicy)) {
			return CommandLine.ExitCode.SOFTWARE;
		}
		if (!resolvePositionals()) {
			return CommandLine.ExitCode.USAGE;
		}
		try {
			boolean dryRunEnabled = resolveDryRun();
			ValuesProfiles profiles = ValuesProfiles
				.of(profileNames.isEmpty() ? coreProperties.getProfiles().getActive() : profileNames);
			if (dependencyUpdate) {
				File localChart = new File(chartPath);
				if (localChart.isDirectory()) {
					dependencyUpdateAction.update(localChart, List.of(), false);
				}
			}
			Chart chart = chartResolver.resolveFromRepo(chartPath, repoOptions.getVersion(), repoOptions.getRepo(),
					repoOptions.hasRepo() ? repoOptions.auth() : null, verify, keyring, profiles);
			if (name == null) {
				name = ReleaseNames.generateName(chart.getMetadata().getName());
			}
			ConfigServerValuesLoader.Result configServer = configServerValuesLoader.load(name, profiles.active(),
					configServerOptions.toOptions());
			Map<String, Object> overrides = ValuesOverrides.parse(valuesFiles, profiles, configServer.values(),
					configServer.overrideNone(), configServer.overrideSystemProperties(), setValues, setStringValues,
					setFileValues, setJsonValues, setLiteralValues);

			Release release = installAction.install(InstallOptions.builder()
				.chart(chart)
				.releaseName(name)
				.namespace(namespace)
				.values(overrides)
				.revision(1)
				.dryRun(dryRunEnabled)
				.serverDryRun(serverDryRun)
				.noHooks(noHooks)
				.createNamespace(createNamespace)
				.description(description)
				.labels(labels)
				.build());
			release = applyCliPostRenderers(release);

			if (!dryRunEnabled && (wait || atomic || waitForJobs)) {
				kubeService.waitForReady(namespace, release.getManifest(), timeout);
			}

			emitResult(release, dryRunEnabled);
			return CommandLine.ExitCode.OK;
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
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

	// Emits the install result: the Helm-shaped release object for -o json|yaml, else the
	// human dry-run summary or the success line.
	private void emitResult(Release release, boolean dryRunEnabled) {
		if (OutputFormat.isJson(output) || OutputFormat.isYaml(output)) {
			Map<String, Object> map = OutputFormat.release(release);
			if (OutputFormat.isJson(output)) {
				System.out.println(OutputFormat.json(map));
			}
			else {
				System.out.print(OutputFormat.yaml(map));
			}
			return;
		}
		if (dryRunEnabled) {
			CliOutput.println(CliOutput.bold("NAME:") + " " + release.getName());
			CliOutput.println(CliOutput.bold("LAST DEPLOYED:") + " " + release.getInfo().getLastDeployed());
			CliOutput.println(CliOutput.bold("NAMESPACE:") + " " + release.getNamespace());
			CliOutput.println(CliOutput.bold("STATUS:") + " " + release.getInfo().getStatus().getValue());
			CliOutput.println(CliOutput.bold("REVISION:") + " " + release.getVersion());
			CliOutput.println("\n" + CliOutput.bold("MANIFEST:") + "\n" + release.getManifest());
		}
		else {
			CliOutput.println(CliOutput.success("Release \"" + name + "\" has been installed."));
		}
	}

	// Resolves the [NAME] CHART positionals into name/chartPath, honouring
	// --generate-name.
	// With --generate-name the NAME is omitted (single positional = chart) and the name
	// is
	// generated after the chart is resolved; otherwise both NAME and CHART are required.
	private boolean resolvePositionals() {
		if (nameAndChart.size() == 2) {
			if (generateName) {
				CliOutput.errPrintln(CliOutput.error("cannot set --generate-name and also specify a release name"));
				return false;
			}
			this.name = nameAndChart.get(0);
			this.chartPath = nameAndChart.get(1);
		}
		else {
			this.chartPath = nameAndChart.get(0);
			if (!generateName) {
				CliOutput.errPrintln(CliOutput.error("must either provide a release name or specify --generate-name"));
				return false;
			}
			this.name = null;
		}
		return true;
	}

	private Release applyCliPostRenderers(Release release) throws IOException {
		if (postRenderers.isEmpty()) {
			return release;
		}
		String manifest = release.getManifest();
		for (String renderer : postRenderers) {
			manifest = new ExternalCommandPostRenderer(List.of(renderer)).process(manifest);
		}
		return release.toBuilder().manifest(manifest).build();
	}

	/**
	 * Resolves the {@code --dry-run} mode. {@code client} (the default when the flag is
	 * given bare) renders locally; {@code server} additionally validates the manifest
	 * against the API server via a server-side dry-run apply (setting
	 * {@link #serverDryRun}); {@code none} (or the flag omitted) disables the dry run.
	 * @return {@code true} if the install should be a dry run
	 */
	private boolean resolveDryRun() {
		this.serverDryRun = false;
		if (dryRun == null || dryRun.isBlank() || "none".equalsIgnoreCase(dryRun)) {
			return false;
		}
		if ("server".equalsIgnoreCase(dryRun)) {
			this.serverDryRun = true;
			return true;
		}
		if (!"client".equalsIgnoreCase(dryRun)) {
			throw new IllegalArgumentException(
					"invalid --dry-run mode '" + dryRun + "' (expected client, server, or none)");
		}
		return true;
	}

}
