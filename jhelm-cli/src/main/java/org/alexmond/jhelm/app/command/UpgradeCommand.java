package org.alexmond.jhelm.app.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.output.OutputFormat;
import org.alexmond.jhelm.core.action.InstallAction;
import org.alexmond.jhelm.core.action.InstallOptions;
import org.alexmond.jhelm.core.action.RollbackAction;
import org.alexmond.jhelm.core.action.RollbackOptions;
import org.alexmond.jhelm.core.action.UninstallAction;
import org.alexmond.jhelm.core.action.UninstallOptions;
import org.alexmond.jhelm.core.action.UpgradeAction;
import org.alexmond.jhelm.core.action.UpgradeOptions;
import org.alexmond.jhelm.core.action.UpgradeValueStrategy;
import org.alexmond.jhelm.core.config.JhelmCoreProperties;
import org.alexmond.jhelm.core.config.JhelmSecurityPolicy;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.Release;
import org.alexmond.jhelm.core.service.ExternalCommandPostRenderer;
import org.alexmond.jhelm.core.service.KubeService;
import org.alexmond.jhelm.core.service.ConfigServerValuesLoader;
import org.alexmond.jhelm.core.util.ValuesOverrides;
import org.alexmond.jhelm.core.util.ValuesProfiles;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * Implements {@code jhelm upgrade RELEASE CHART}, upgrading an existing release. Supports
 * installing when the release is absent ({@code --install}), values overrides, dry-run,
 * waiting for readiness, atomic rollback on failure, and post-renderers.
 */
@Component
@CommandLine.Command(name = "upgrade", mixinStandardHelpOptions = true, description = "Upgrade a release")
@Slf4j
public class UpgradeCommand implements Callable<Integer> {

	private final KubeService kubeService;

	private final InstallAction installAction;

	private final UninstallAction uninstallAction;

	private final UpgradeAction upgradeAction;

	private final RollbackAction rollbackAction;

	private final ChartResolver chartResolver;

	private final JhelmSecurityPolicy securityPolicy;

	private final JhelmCoreProperties coreProperties;

	private final ConfigServerValuesLoader configServerValuesLoader;

	@CommandLine.Mixin
	private final ConfigServerCliOptions configServerOptions = new ConfigServerCliOptions();

	@CommandLine.Parameters(index = "0", description = "release name")
	private String name;

	@CommandLine.Parameters(index = "1", description = "chart path")
	private String chartPath;

	@Option(names = { "--verify" }, description = "verify the packaged chart's provenance before upgrading")
	private boolean verify;

	@Option(names = { "--keyring" }, description = "path to the PGP public keyring file (for --verify)")
	private String keyring;

	@CommandLine.Option(names = { "-n", "--namespace" }, defaultValue = "default", description = "namespace")
	private String namespace;

	@CommandLine.Option(names = { "--install" }, description = "install if not exists")
	private boolean install;

	@CommandLine.Option(names = { "--dry-run" }, arity = "0..1", fallbackValue = "client", paramLabel = "MODE",
			description = "simulate an upgrade without applying: client (default), server, or none")
	private String dryRun;

	private boolean dryRunEnabled;

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

	@Option(names = { "--atomic" }, description = "rollback on failure (implies --wait)")
	private boolean atomic;

	@Option(names = { "--force" }, description = "force resource updates through a delete-and-recreate strategy "
			+ "(may cause downtime or data loss for stateful resources)")
	private boolean force;

	@Option(names = { "--post-renderer" }, description = "path to an executable to use as a post-renderer")
	private List<String> postRenderers = new ArrayList<>();

	@Option(names = { "--reset-values" },
			description = "when upgrading, reset the values to the ones built into the chart")
	private boolean resetValues;

	@Option(names = { "--reuse-values" },
			description = "when upgrading, reuse the last release's values and merge in any overrides from the command line via --set and -f")
	private boolean reuseValues;

	@Option(names = { "--reset-then-reuse-values" },
			description = "when upgrading, reset the values to the ones built into the chart, apply the last release's values and merge in any overrides from the command line via --set and -f")
	private boolean resetThenReuseValues;

	@Option(names = { "--no-hooks" }, description = "prevent hooks from running during this operation")
	private boolean noHooks;

	@CommandLine.Option(names = { "-o", "--output" }, defaultValue = "table",
			description = "output format: table (default human summary), json, or yaml")
	private String output;

	@CommandLine.Option(names = { "--history-max" }, defaultValue = "10",
			description = "limit the maximum number of revisions saved per release (0 = no limit)")
	private int historyMax;

	@Option(names = { "--description" }, description = "add a custom description to the new revision")
	private String description;

	@Option(names = { "--labels" }, split = ",",
			description = "labels to apply to the release Secret (key=value, comma-separated)")
	private Map<String, String> labels;

	/**
	 * Creates the command.
	 * @param kubeService the Kubernetes service used to look up releases and wait for
	 * readiness
	 * @param installAction the action used to install when {@code --install} is set
	 * @param uninstallAction the action used to roll back a freshly installed release on
	 * atomic failure
	 * @param upgradeAction the action that performs the upgrade
	 * @param rollbackAction the action used to roll back to the previous revision on
	 * atomic failure
	 * @param chartResolver resolves the chart source (directory or {@code .tgz}), with
	 * optional provenance verification
	 * @param securityPolicy the unified access-mode policy; the operation is refused
	 * unless mutating operations are enabled
	 */
	public UpgradeCommand(KubeService kubeService, InstallAction installAction, UninstallAction uninstallAction,
			UpgradeAction upgradeAction, RollbackAction rollbackAction, ChartResolver chartResolver,
			JhelmSecurityPolicy securityPolicy, JhelmCoreProperties coreProperties,
			ConfigServerValuesLoader configServerValuesLoader) {
		this.kubeService = kubeService;
		this.installAction = installAction;
		this.uninstallAction = uninstallAction;
		this.upgradeAction = upgradeAction;
		this.rollbackAction = rollbackAction;
		this.chartResolver = chartResolver;
		this.securityPolicy = securityPolicy;
		this.coreProperties = coreProperties;
		this.configServerValuesLoader = configServerValuesLoader;
	}

	@Override
	public Integer call() {
		if (MutatingGuard.blocked(securityPolicy)) {
			return CommandLine.ExitCode.SOFTWARE;
		}
		int previousVersion = -1;
		boolean wasInstall = false;
		try {
			this.dryRunEnabled = resolveDryRun();
			Optional<Release> currentReleaseOpt = kubeService.getRelease(name, namespace);
			ValuesProfiles profiles = ValuesProfiles
				.of(profileNames.isEmpty() ? coreProperties.getProfiles().getActive() : profileNames);
			ConfigServerValuesLoader.Result configServer = configServerValuesLoader.load(name, profiles.active(),
					configServerOptions.toOptions());
			Chart chart = chartResolver.resolve(chartPath, verify, keyring, profiles);
			Map<String, Object> overrides = ValuesOverrides.parse(valuesFiles, profiles, configServer.values(),
					configServer.overrideNone(), configServer.overrideSystemProperties(), setValues, setStringValues,
					setFileValues, setJsonValues, setLiteralValues);

			if (currentReleaseOpt.isEmpty()) {
				if (install) {
					wasInstall = true;
					Release release = installAction.install(buildInstallOptions(chart, overrides));
					release = applyCliPostRenderers(release);
					if (!dryRunEnabled && (wait || atomic || waitForJobs)) {
						kubeService.waitForReady(namespace, release.getManifest(), timeout);
					}
					if (!emitMachineOutput(release)) {
						if (dryRunEnabled) {
							printRelease(release);
						}
						else {
							CliOutput.println(
									CliOutput.success("Release \"" + name + "\" does not exist. Installing it now."));
						}
					}
				}
				else {
					CliOutput.errPrintln(CliOutput.error("Error: release \"" + name + "\" does not exist"));
					return CommandLine.ExitCode.SOFTWARE;
				}
				return CommandLine.ExitCode.OK;
			}

			previousVersion = currentReleaseOpt.get().getVersion();
			UpgradeValueStrategy strategy = (resetValues) ? UpgradeValueStrategy.RESET
					: (resetThenReuseValues) ? UpgradeValueStrategy.RESET_THEN_REUSE
							: (reuseValues) ? UpgradeValueStrategy.REUSE : UpgradeValueStrategy.DEFAULT;
			Release upgradedRelease = upgradeAction
				.upgrade(buildUpgradeOptions(currentReleaseOpt.get(), chart, overrides, strategy));
			upgradedRelease = applyCliPostRenderers(upgradedRelease);

			if (!dryRunEnabled && (wait || atomic || waitForJobs)) {
				kubeService.waitForReady(namespace, upgradedRelease.getManifest(), timeout);
			}
			if (!emitMachineOutput(upgradedRelease)) {
				if (dryRunEnabled) {
					printRelease(upgradedRelease);
				}
				else {
					CliOutput.println(CliOutput.success("Release \"" + name + "\" has been upgraded. Happy Helming!"));
				}
			}
			return CommandLine.ExitCode.OK;
		}
		catch (Exception ex) {
			if (atomic) {
				performAtomicRollback(ex, wasInstall, previousVersion);
			}
			else {
				CliOutput.errPrintln(CliOutput.error("Error upgrading release: " + ex.getMessage()));
			}
			if (log.isDebugEnabled()) {
				log.debug("Upgrade error details", ex);
			}
			return CommandLine.ExitCode.SOFTWARE;
		}
	}

	/**
	 * Rolls the release back after an {@code --atomic} upgrade failure: uninstalls a
	 * freshly-installed release, otherwise rolls back to the previous revision. Any
	 * failure of the rollback itself is reported but does not mask the original error.
	 * @param ex the upgrade failure that triggered the rollback
	 * @param wasInstall {@code true} if the release was freshly installed in this run
	 * @param previousVersion the revision to roll back to, or negative if none exists
	 */
	private void performAtomicRollback(Exception ex, boolean wasInstall, int previousVersion) {
		CliOutput.errPrintln(CliOutput.error("Upgrade failed, performing atomic rollback: " + ex.getMessage()));
		try {
			if (wasInstall || previousVersion < 0) {
				uninstallAction.uninstall(UninstallOptions.builder().releaseName(name).namespace(namespace).build());
				CliOutput.println("Atomic uninstall of \"" + name + "\" complete.");
			}
			else {
				rollbackAction.rollback(RollbackOptions.builder()
					.releaseName(name)
					.namespace(namespace)
					.revision(previousVersion)
					.build());
				CliOutput.println("Atomic rollback of \"" + name + "\" complete.");
			}
		}
		catch (Exception rollbackEx) {
			CliOutput.errPrintln(CliOutput.error("Atomic rollback failed: " + rollbackEx.getMessage()));
		}
	}

	private InstallOptions buildInstallOptions(Chart chart, Map<String, Object> overrides) {
		return InstallOptions.builder()
			.chart(chart)
			.releaseName(name)
			.namespace(namespace)
			.values(overrides)
			.revision(1)
			.dryRun(dryRunEnabled)
			.serverDryRun(serverDryRun)
			.noHooks(noHooks)
			.build();
	}

	private UpgradeOptions buildUpgradeOptions(Release current, Chart chart, Map<String, Object> overrides,
			UpgradeValueStrategy strategy) {
		return UpgradeOptions.builder()
			.currentRelease(current)
			.newChart(chart)
			.values(overrides)
			.valueStrategy(strategy)
			.dryRun(dryRunEnabled)
			.serverDryRun(serverDryRun)
			.noHooks(noHooks)
			.maxHistory(historyMax)
			.force(force)
			.description(description)
			.labels((labels != null) ? labels : Map.of())
			.build();
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

	private void printRelease(Release release) {
		CliOutput.println(CliOutput.bold("NAME:") + " " + release.getName());
		CliOutput.println(CliOutput.bold("LAST DEPLOYED:") + " " + release.getInfo().getLastDeployed());
		CliOutput.println(CliOutput.bold("NAMESPACE:") + " " + release.getNamespace());
		CliOutput.println(CliOutput.bold("STATUS:") + " " + release.getInfo().getStatus().getValue());
		CliOutput.println(CliOutput.bold("REVISION:") + " " + release.getVersion());
		CliOutput.println("\n" + CliOutput.bold("MANIFEST:") + "\n" + release.getManifest());
	}

	// Emits the release as json/yaml when -o requests it; returns true if it did.
	private boolean emitMachineOutput(Release release) {
		if (!OutputFormat.isJson(output) && !OutputFormat.isYaml(output)) {
			return false;
		}
		Map<String, Object> map = OutputFormat.release(release);
		if (OutputFormat.isJson(output)) {
			System.out.println(OutputFormat.json(map));
		}
		else {
			System.out.print(OutputFormat.yaml(map));
		}
		return true;
	}

	/**
	 * Resolves the {@code --dry-run} mode. {@code client} (the default when the flag is
	 * given bare) renders locally; {@code server} additionally validates the manifest
	 * against the API server via a server-side dry-run apply (setting
	 * {@link #serverDryRun}); {@code none} (or the flag omitted) disables the dry run.
	 * @return {@code true} if the upgrade should be a dry run
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
