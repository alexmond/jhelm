package org.alexmond.jhelm.app.command;

import tools.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.app.output.CliOutput;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.ChartLock;
import org.alexmond.jhelm.core.model.Dependency;
import org.alexmond.jhelm.core.service.DependencyResolver;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.util.ValuesLoader;
import org.alexmond.jhelm.core.model.ChartLock.LockDependency;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the {@code helm dependency} command and its subcommands.
 * <p>
 * Manages chart dependencies as defined in Chart.yaml:
 * <ul>
 * <li>{@code dependency list} - List dependencies and their status</li>
 * <li>{@code dependency update} - Update dependencies from Chart.yaml</li>
 * <li>{@code dependency build} - Rebuild dependencies from Chart.lock</li>
 * </ul>
 */
@Component
@CommandLine.Command(name = "dependency", mixinStandardHelpOptions = true,
		description = "Manage a chart's dependencies",
		subcommands = { DependencyCommand.ListCommand.class, DependencyCommand.UpdateCommand.class,
				DependencyCommand.BuildCommand.class })
@Slf4j
public class DependencyCommand implements Runnable {

	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

	/**
	 * Implements {@code helm dependency list [CHART]}.
	 * <p>
	 * Lists the dependencies for the chart, showing their status.
	 */
	@Component
	@CommandLine.Command(name = "list", mixinStandardHelpOptions = true,
			description = "List the dependencies for the given chart")
	@Slf4j
	public static class ListCommand implements Runnable {

		@CommandLine.Parameters(index = "0", description = "chart directory", defaultValue = ".")
		String chartPath;

		@Override
		public void run() {
			try {
				File chartDir = new File(chartPath);
				if (!chartDir.exists() || !chartDir.isDirectory()) {
					CliOutput.errPrintln(CliOutput.error("Error: Chart directory not found: " + chartPath));
					return;
				}

				// Load Chart.yaml
				ChartMetadata metadata = loadChartMetadata(chartDir);
				if (metadata.getDependencies() == null || metadata.getDependencies().isEmpty()) {
					CliOutput.println("No dependencies found in Chart.yaml");
					return;
				}

				// Load Chart.lock if it exists
				ChartLock chartLock = ChartLock.fromFile(chartDir);
				Map<String, LockDependency> lockMap = new HashMap<>();
				if (chartLock != null && chartLock.getDependencies() != null) {
					for (LockDependency lockDep : chartLock.getDependencies()) {
						lockMap.put(lockDep.getName(), lockDep);
					}
				}

				// Check charts/ directory
				File chartsDir = new File(chartDir, "charts");
				Set<String> unpackedCharts = new HashSet<>();
				if (chartsDir.exists() && chartsDir.isDirectory()) {
					File[] files = chartsDir.listFiles();
					if (files != null) {
						for (File f : files) {
							if (f.isDirectory()) {
								unpackedCharts.add(f.getName());
							}
						}
					}
				}

				// Print header
				CliOutput.printf("%-20s %-15s %-40s %s%n", CliOutput.bold("NAME"), CliOutput.bold("VERSION"),
						CliOutput.bold("REPOSITORY"), CliOutput.bold("STATUS"));

				// Print each dependency
				for (Dependency dep : metadata.getDependencies()) {
					String name = dep.getName();
					String version = dep.getVersion();
					String repository = dep.getRepository();
					String status = determineStatus(dep, lockMap.get(name), unpackedCharts);

					CliOutput.printf("%-20s %-15s %-40s %s%n", name, version, (repository != null) ? repository : "",
							colorizeDependencyStatus(status));
				}
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error listing dependencies: " + ex.getMessage()));
				if (log.isDebugEnabled()) {
					log.debug("Dependency list error details", ex);
				}
			}
		}

		private String colorizeDependencyStatus(String status) {
			if (status == null) {
				return "";
			}
			return switch (status.toLowerCase(Locale.ROOT)) {
				case "ok" -> CliOutput.success(status);
				case "missing", "wrong version" -> CliOutput.error(status);
				case "unpacked" -> CliOutput.warn(status);
				default -> status;
			};
		}

		private String determineStatus(Dependency dep, LockDependency lockDep, Set<String> unpackedCharts) {
			String name = dep.getName();

			// Check if unpacked
			boolean isUnpacked = unpackedCharts.contains(name);

			if (lockDep == null) {
				return isUnpacked ? "unpacked" : "missing";
			}

			// Check version match
			if (!dep.getVersion().equals(lockDep.getVersion())) {
				return "wrong version";
			}

			return isUnpacked ? "ok" : "missing";
		}

		private ChartMetadata loadChartMetadata(File chartDir) throws Exception {
			File chartFile = new File(chartDir, "Chart.yaml");
			if (!chartFile.exists()) {
				throw new Exception("Chart.yaml not found in " + chartDir.getAbsolutePath());
			}

			YAMLMapper yamlMapper = YAMLMapper.builder().build();
			return yamlMapper.readValue(chartFile, ChartMetadata.class);
		}

	}

	/**
	 * Implements {@code helm dependency update [CHART]}.
	 * <p>
	 * Updates dependencies from Chart.yaml to the charts/ directory, resolving version
	 * constraints and generating Chart.lock.
	 */
	@Component
	@CommandLine.Command(name = "update", mixinStandardHelpOptions = true,
			description = "Update charts/ based on the contents of Chart.yaml")
	@Slf4j
	public static class UpdateCommand implements Runnable {

		private final RepoManager repoManager;

		@CommandLine.Parameters(index = "0", description = "chart directory", defaultValue = ".")
		String chartPath;

		@CommandLine.Option(names = { "--skip-refresh" }, description = "Skip refreshing the local repository cache")
		boolean skipRefresh;

		@CommandLine.Option(names = { "--with-tags" },
				description = "Enable dependency tags to include (comma-separated)")
		List<String> withTags = new ArrayList<>();

		public UpdateCommand(RepoManager repoManager) {
			this.repoManager = repoManager;
		}

		@Override
		public void run() {
			try {
				File chartDir = new File(chartPath);
				if (!chartDir.exists() || !chartDir.isDirectory()) {
					CliOutput.errPrintln(CliOutput.error("Error: Chart directory not found: " + chartPath));
					return;
				}

				// Refresh repositories unless --skip-refresh
				if (!skipRefresh) {
					CliOutput.println("Hang tight while we grab the latest from your chart repositories...");
					repoManager.updateAll();
					CliOutput.println(CliOutput.success("...Successfully got an update from the chart repositories"));
				}

				// Load Chart.yaml
				ChartMetadata metadata = loadChartMetadata(chartDir);
				if (metadata.getDependencies() == null || metadata.getDependencies().isEmpty()) {
					CliOutput.println("No dependencies found in Chart.yaml");
					return;
				}

				// Load values.yaml for condition evaluation
				Map<String, Object> values = loadValues(chartDir);

				// Resolve dependencies
				DependencyResolver resolver = new DependencyResolver(repoManager);
				ChartLock chartLock = resolver.resolveDependencies(metadata, values,
						withTags.isEmpty() ? null : withTags);

				// Download dependencies
				CliOutput.println("Updating dependencies from Chart.yaml...");
				resolver.downloadDependencies(chartDir, chartLock.getDependencies());

				// Save Chart.lock
				chartLock.toFile(chartDir);
				CliOutput.println("Saving " + chartLock.getDependencies().size() + " charts");
				CliOutput.println(CliOutput.success("Dependency update complete."));
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error updating dependencies: " + ex.getMessage()));
				if (log.isDebugEnabled()) {
					log.debug("Dependency update error details", ex);
				}
			}
		}

		private ChartMetadata loadChartMetadata(File chartDir) throws Exception {
			File chartFile = new File(chartDir, "Chart.yaml");
			if (!chartFile.exists()) {
				throw new Exception("Chart.yaml not found in " + chartDir.getAbsolutePath());
			}

			YAMLMapper yamlMapper = YAMLMapper.builder().build();
			return yamlMapper.readValue(chartFile, ChartMetadata.class);
		}

		private Map<String, Object> loadValues(File chartDir) {
			try {
				File valuesFile = new File(chartDir, "values.yaml");
				if (!valuesFile.exists()) {
					return new HashMap<>();
				}
				return ValuesLoader.load(valuesFile);
			}
			catch (Exception ex) {
				if (log.isWarnEnabled()) {
					log.warn("Failed to load values.yaml: {}", ex.getMessage());
				}
				return new HashMap<>();
			}
		}

	}

	/**
	 * Implements {@code helm dependency build [CHART]}.
	 * <p>
	 * Rebuilds the charts/ directory from Chart.lock.
	 */
	@Component
	@CommandLine.Command(name = "build", mixinStandardHelpOptions = true,
			description = "Rebuild the charts/ directory based on Chart.lock")
	@Slf4j
	public static class BuildCommand implements Runnable {

		private final RepoManager repoManager;

		@CommandLine.Parameters(index = "0", description = "chart directory", defaultValue = ".")
		String chartPath;

		@CommandLine.Option(names = { "--skip-refresh" }, description = "Skip refreshing the local repository cache")
		boolean skipRefresh;

		public BuildCommand(RepoManager repoManager) {
			this.repoManager = repoManager;
		}

		@Override
		public void run() {
			try {
				File chartDir = new File(chartPath);
				if (!chartDir.exists() || !chartDir.isDirectory()) {
					CliOutput.errPrintln(CliOutput.error("Error: Chart directory not found: " + chartPath));
					return;
				}

				// Load Chart.lock
				ChartLock chartLock = ChartLock.fromFile(chartDir);
				if (chartLock == null) {
					CliOutput
						.errPrintln(CliOutput.error("Error: Chart.lock not found. Run 'dependency update' first."));
					return;
				}

				if (chartLock.getDependencies() == null || chartLock.getDependencies().isEmpty()) {
					CliOutput.println("No dependencies found in Chart.lock");
					return;
				}

				// Refresh repositories unless --skip-refresh
				if (!skipRefresh) {
					CliOutput.println("Hang tight while we grab the latest from your chart repositories...");
					repoManager.updateAll();
					CliOutput.println(CliOutput.success("...Successfully got an update from the chart repositories"));
				}

				// Download dependencies
				CliOutput.println("Building dependencies from Chart.lock...");
				DependencyResolver resolver = new DependencyResolver(repoManager);
				resolver.downloadDependencies(chartDir, chartLock.getDependencies());

				CliOutput.println("Saving " + chartLock.getDependencies().size() + " charts");
				CliOutput.println(CliOutput.success("Dependency build complete."));
			}
			catch (Exception ex) {
				CliOutput.errPrintln(CliOutput.error("Error building dependencies: " + ex.getMessage()));
				if (log.isDebugEnabled()) {
					log.debug("Dependency build error details", ex);
				}
			}
		}

	}

}
