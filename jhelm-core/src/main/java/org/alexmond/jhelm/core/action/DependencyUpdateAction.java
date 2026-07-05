package org.alexmond.jhelm.core.action;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alexmond.jhelm.core.model.ChartLock;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.service.DependencyResolver;
import org.alexmond.jhelm.core.service.RepoManager;
import org.alexmond.jhelm.core.util.ValuesLoader;

import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Updates a chart's {@code charts/} directory from its {@code Chart.yaml} dependencies,
 * resolving version constraints and writing {@code Chart.lock} — the reusable core of
 * {@code helm dependency update}.
 * <p>
 * Extracted so the {@code dependency update} command and the {@code --dependency-update}
 * flag on {@code install}/{@code upgrade}/{@code template} share one implementation.
 */
public class DependencyUpdateAction {

	private final RepoManager repoManager;

	private final DependencyResolver resolver;

	/**
	 * Creates the action.
	 * @param repoManager the repository manager used to refresh caches and download
	 * dependency charts
	 */
	public DependencyUpdateAction(RepoManager repoManager) {
		this(repoManager, new DependencyResolver(repoManager));
	}

	/**
	 * Creates the action with an explicit resolver (test seam).
	 * @param repoManager the repository manager used to refresh caches
	 * @param resolver the dependency resolver used to resolve and download dependencies
	 */
	DependencyUpdateAction(RepoManager repoManager, DependencyResolver resolver) {
		this.repoManager = repoManager;
		this.resolver = resolver;
	}

	/**
	 * Resolves the chart's declared dependencies, downloads them into {@code charts/},
	 * and writes {@code Chart.lock}.
	 * @param chartDir the local chart directory
	 * @param withTags dependency tags to enable ({@code null}/empty = none)
	 * @param skipRefresh when {@code true}, skip refreshing the local repository cache
	 * @return the resolved {@link ChartLock}; its dependency list is empty (and no lock
	 * is written) when the chart declares no dependencies
	 * @throws IOException if resolution or download fails
	 */
	public ChartLock update(File chartDir, List<String> withTags, boolean skipRefresh) throws IOException {
		if (!chartDir.isDirectory()) {
			throw new IllegalArgumentException("Chart directory not found: " + chartDir.getAbsolutePath());
		}
		if (!skipRefresh) {
			this.repoManager.updateAll();
		}
		ChartMetadata metadata = loadChartMetadata(chartDir);
		if (metadata.getDependencies() == null || metadata.getDependencies().isEmpty()) {
			return ChartLock.builder().dependencies(List.of()).build();
		}
		Map<String, Object> values = loadValues(chartDir);
		List<String> tags = (withTags == null || withTags.isEmpty()) ? null : withTags;
		ChartLock lock = this.resolver.resolveDependencies(metadata, values, tags);
		this.resolver.downloadDependencies(chartDir, lock.getDependencies());
		lock.toFile(chartDir);
		return lock;
	}

	private ChartMetadata loadChartMetadata(File chartDir) {
		File chartFile = new File(chartDir, "Chart.yaml");
		if (!chartFile.exists()) {
			throw new IllegalStateException("Chart.yaml not found in " + chartDir.getAbsolutePath());
		}
		YAMLMapper yamlMapper = YAMLMapper.builder().build();
		return yamlMapper.readValue(chartFile, ChartMetadata.class);
	}

	private Map<String, Object> loadValues(File chartDir) throws IOException {
		File valuesFile = new File(chartDir, "values.yaml");
		return valuesFile.exists() ? ValuesLoader.load(valuesFile) : new HashMap<>();
	}

}
