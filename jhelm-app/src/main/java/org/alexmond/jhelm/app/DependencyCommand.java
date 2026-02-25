package org.alexmond.jhelm.app;

import tools.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.ChartMetadata;
import org.alexmond.jhelm.core.ChartLock;
import org.alexmond.jhelm.core.Dependency;
import org.alexmond.jhelm.core.DependencyResolver;
import org.alexmond.jhelm.core.RepoManager;
import org.alexmond.jhelm.core.ValuesLoader;
import org.alexmond.jhelm.core.ChartLock.LockDependency;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

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
@CommandLine.Command(name = "dependency", description = "Manage a chart's dependencies",
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
	@CommandLine.Command(name = "list", description = "List the dependencies for the given chart")
	@Slf4j
	public static class ListCommand implements Runnable {

		@CommandLine.Parameters(index = "0", description = "chart directory", defaultValue = ".")
		String chartPath;

		@Override
		public void run() {
			try {
				File chartDir = new File(chartPath);
				if (!chartDir.exists() || !chartDir.isDirectory()) {
					System.err.println("Error: Chart directory not found: " + chartPath);
					return;
				}

				// Load Chart.yaml
				ChartMetadata metadata = loadChartMetadata(chartDir);
				if (metadata.getDependencies() == null || metadata.getDependencies().isEmpty()) {
					System.out.println("No dependencies found in Chart.yaml");
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
				System.out.printf("%-20s %-15s %-40s %s%n", "NAME", "VERSION", "REPOSITORY", "STATUS");

				// Print each dependency
				for (Dependency dep : metadata.getDependencies()) {
					String name = dep.getName();
					String version = dep.getVersion();
					String repository = dep.getRepository();
					String status = determineStatus(dep, lockMap.get(name), unpackedCharts);

					System.out.printf("%-20s %-15s %-40s %s%n", name, version, (repository != null) ? repository : "",
							status);
				}
			}
			catch (Exception ex) {
				log.error("Error listing dependencies: {}", ex.getMessage(), ex);
				System.err.println("Error listing dependencies: " + ex.getMessage());
			}
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
	@CommandLine.Command(name = "update", description = "Update charts/ based on the contents of Chart.yaml")
	@Slf4j
	public static class UpdateCommand implements Runnable {

		private final RepoManager repoManager;

		@CommandLine.Parameters(index = "0", description = "chart directory", defaultValue = ".")
		String chartPath;

		@CommandLine.Option(names = { "--skip-refresh" }, description = "Skip refreshing the local repository cache")
		boolean skipRefresh;

		@CommandLine.Option(names = { "--with-tags" },
				description = "Enable dependency tags to include (comma-separated)")
		java.util.List<String> withTags = new java.util.ArrayList<>();

		public UpdateCommand(RepoManager repoManager) {
			this.repoManager = repoManager;
		}

		@Override
		public void run() {
			try {
				File chartDir = new File(chartPath);
				if (!chartDir.exists() || !chartDir.isDirectory()) {
					System.err.println("Error: Chart directory not found: " + chartPath);
					return;
				}

				// Refresh repositories unless --skip-refresh
				if (!skipRefresh) {
					System.out.println("Hang tight while we grab the latest from your chart repositories...");
					repoManager.updateAll();
					System.out.println("...Successfully got an update from the chart repositories");
				}

				// Load Chart.yaml
				ChartMetadata metadata = loadChartMetadata(chartDir);
				if (metadata.getDependencies() == null || metadata.getDependencies().isEmpty()) {
					System.out.println("No dependencies found in Chart.yaml");
					return;
				}

				// Load values.yaml for condition evaluation
				Map<String, Object> values = loadValues(chartDir);

				// Resolve dependencies
				DependencyResolver resolver = new DependencyResolver(repoManager);
				ChartLock chartLock = resolver.resolveDependencies(metadata, values,
						withTags.isEmpty() ? null : withTags);

				// Download dependencies
				System.out.println("Updating dependencies from Chart.yaml...");
				resolver.downloadDependencies(chartDir, chartLock.getDependencies());

				// Save Chart.lock
				chartLock.toFile(chartDir);
				System.out.println("Saving " + chartLock.getDependencies().size() + " charts");
				System.out.println("Dependency update complete.");
			}
			catch (Exception ex) {
				log.error("Error updating dependencies: {}", ex.getMessage(), ex);
				System.err.println("Error updating dependencies: " + ex.getMessage());
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
				log.warn("Failed to load values.yaml: {}", ex.getMessage());
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
	@CommandLine.Command(name = "build", description = "Rebuild the charts/ directory based on Chart.lock")
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
					System.err.println("Error: Chart directory not found: " + chartPath);
					return;
				}

				// Load Chart.lock
				ChartLock chartLock = ChartLock.fromFile(chartDir);
				if (chartLock == null) {
					System.err.println("Error: Chart.lock not found. Run 'dependency update' first.");
					return;
				}

				if (chartLock.getDependencies() == null || chartLock.getDependencies().isEmpty()) {
					System.out.println("No dependencies found in Chart.lock");
					return;
				}

				// Refresh repositories unless --skip-refresh
				if (!skipRefresh) {
					System.out.println("Hang tight while we grab the latest from your chart repositories...");
					repoManager.updateAll();
					System.out.println("...Successfully got an update from the chart repositories");
				}

				// Download dependencies
				System.out.println("Building dependencies from Chart.lock...");
				DependencyResolver resolver = new DependencyResolver(repoManager);
				resolver.downloadDependencies(chartDir, chartLock.getDependencies());

				System.out.println("Saving " + chartLock.getDependencies().size() + " charts");
				System.out.println("Dependency build complete.");
			}
			catch (Exception ex) {
				log.error("Error building dependencies: {}", ex.getMessage(), ex);
				System.err.println("Error building dependencies: " + ex.getMessage());
			}
		}

	}

}
