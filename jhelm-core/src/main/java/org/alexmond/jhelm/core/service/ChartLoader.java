package org.alexmond.jhelm.core.service;

import tools.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.ChartLoadException;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.model.Dependency;
import org.alexmond.jhelm.core.util.ValuesLoader;

/**
 * Loads a Helm chart from a directory on disk into an in-memory {@link Chart}, reading
 * {@code Chart.yaml} metadata, {@code values.yaml}, templates, CRDs, subchart
 * dependencies and arbitrary non-template files exposed via the {@code .Files} object.
 */
@Slf4j
@Component
public class ChartLoader {

	private final YAMLMapper yamlMapper = YAMLMapper.builder().build();

	/**
	 * Finds the first subdirectory inside a parent directory. Charts extracted from .tgz
	 * archives create a single subdirectory (e.g. {@code parent/nginx/}).
	 * @param parent the directory to search
	 * @return the first subdirectory, or the parent itself if none found
	 * @throws IOException if the directory cannot be listed
	 */
	public static Path findChartDir(Path parent) throws IOException {
		try (var stream = Files.list(parent)) {
			return stream.filter(Files::isDirectory).findFirst().orElse(parent);
		}
	}

	/**
	 * Loads the chart rooted at the given directory, including its metadata, values,
	 * templates, CRDs and subchart dependencies.
	 * @param chartDir the chart's root directory (the one containing {@code Chart.yaml})
	 * @return the fully populated chart
	 * @throws ChartLoadException if the directory is missing, has no {@code Chart.yaml},
	 * or a chart file cannot be read
	 */
	public Chart load(File chartDir) {
		if (!chartDir.exists() || !chartDir.isDirectory()) {
			throw new ChartLoadException("Chart directory does not exist", chartDir.getPath(),
					"Verify the path is correct and points to a valid Helm chart directory");
		}

		// Load Chart.yaml
		File metadataFile = new File(chartDir, "Chart.yaml");
		if (!metadataFile.exists()) {
			throw new ChartLoadException("Chart.yaml not found", chartDir.getPath(),
					"A valid Helm chart requires a Chart.yaml file. Run 'helm create' to scaffold a new chart");
		}

		try {
			return loadFromDir(chartDir, metadataFile);
		}
		catch (IOException ex) {
			throw new ChartLoadException("Failed to load chart", ex, chartDir.getPath(),
					"Verify the chart files are readable and well-formed");
		}
	}

	private Chart loadFromDir(File chartDir, File metadataFile) throws IOException {
		ChartMetadata metadata = yamlMapper.readValue(metadataFile, ChartMetadata.class);

		// For apiVersion v1 charts, dependencies live in requirements.yaml rather than
		// Chart.yaml. Load them so that condition/alias metadata is available during
		// rendering.
		if (metadata.getDependencies() == null || metadata.getDependencies().isEmpty()) {
			File requirementsFile = new File(chartDir, "requirements.yaml");
			if (requirementsFile.exists()) {
				ChartMetadata reqMeta = yamlMapper.readValue(requirementsFile, ChartMetadata.class);
				if (reqMeta.getDependencies() != null) {
					metadata.setDependencies(reqMeta.getDependencies());
				}
			}
		}

		// Load values.yaml (supports multi-document files separated by ---)
		File valuesFile = new File(chartDir, "values.yaml");
		Map<String, Object> values = new LinkedHashMap<>();
		if (valuesFile.exists()) {
			values = ValuesLoader.load(valuesFile);
		}

		// Load templates
		File templatesDir = new File(chartDir, "templates");
		List<Chart.Template> templates = new ArrayList<>();
		if (templatesDir.exists() && templatesDir.isDirectory()) {
			loadTemplatesRecursive(templatesDir, "", templates);
		}

		// Load dependencies (subcharts)
		List<Chart> dependencies = loadDependencies(chartDir, metadata.getDependencies());

		// Load README
		String readme = null;
		File readmeFile = new File(chartDir, "README.md");
		if (readmeFile.exists()) {
			readme = Files.readString(readmeFile.toPath());
		}

		// Load values.schema.json
		String valuesSchema = null;
		File valuesSchemaFile = new File(chartDir, "values.schema.json");
		if (valuesSchemaFile.exists()) {
			valuesSchema = Files.readString(valuesSchemaFile.toPath());
		}

		// Load CRDs
		File crdsDir = new File(chartDir, "crds");
		List<Chart.Crd> crds = new ArrayList<>();
		if (crdsDir.exists() && crdsDir.isDirectory()) {
			loadCrdsRecursive(crdsDir, "", crds);
		}

		// Load non-template files (for .Files object)
		Map<String, String> chartFiles = new LinkedHashMap<>();
		loadChartFiles(chartDir, chartFiles);

		return Chart.builder()
			.metadata(metadata)
			.values(values)
			.valuesSchema(valuesSchema)
			.templates(templates)
			.dependencies(dependencies)
			.readme(readme)
			.crds(crds)
			.files(chartFiles)
			.build();
	}

	private List<Chart> loadDependencies(File chartDir, List<Dependency> metaDeps) throws IOException {
		File chartsDir = new File(chartDir, "charts");
		List<Chart> dependencies = new ArrayList<>();
		if (!chartsDir.exists() || !chartsDir.isDirectory()) {
			return dependencies;
		}
		File[] subchartDirs = chartsDir.listFiles(File::isDirectory);
		if (subchartDirs != null) {
			for (File subchartDir : subchartDirs) {
				Chart subchart = load(subchartDir);
				if (!subchartDir.getName().equals(subchart.getMetadata().getName())) {
					subchart.setAlias(subchartDir.getName());
				}
				dependencies.add(subchart);
				addAliasInstances(dependencies, subchart, metaDeps);
			}
		}
		return dependencies;
	}

	/**
	 * Helm stores a subchart aliased multiple times (e.g. grafana-loki aliases the single
	 * {@code memcached} chart as {@code memcachedfrontend}/{@code memcachedchunks}/…) as
	 * one directory, instantiated once per alias at render time. The on-disk chart is
	 * loaded once, so for every alias beyond the first add a copy (with its own metadata)
	 * addressed by that alias; the base keeps the first alias. A single (or no) alias is
	 * left to the engine's alias application.
	 * @param dependencies the dependency list being built (appended to)
	 * @param base the subchart loaded once from disk
	 * @param metaDeps the parent's Chart.yaml dependency declarations
	 */
	private void addAliasInstances(List<Chart> dependencies, Chart base, List<Dependency> metaDeps) {
		if (metaDeps == null) {
			return;
		}
		List<Dependency> aliases = new ArrayList<>();
		for (Dependency dep : metaDeps) {
			if (base.getMetadata().getName().equals(dep.getName()) && dep.getAlias() != null
					&& !dep.getAlias().isEmpty()) {
				aliases.add(dep);
			}
		}
		if (aliases.size() <= 1) {
			return;
		}
		base.setAlias(aliases.get(0).getAlias());
		for (int i = 1; i < aliases.size(); i++) {
			ChartMetadata metaCopy = base.getMetadata().toBuilder().build();
			Chart copy = base.toBuilder().metadata(metaCopy).alias(aliases.get(i).getAlias()).build();
			dependencies.add(copy);
		}
	}

	private void loadTemplatesRecursive(File dir, String path, List<Chart.Template> templates) throws IOException {
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			String name = path.isEmpty() ? file.getName() : path + "/" + file.getName();
			if (file.isDirectory()) {
				loadTemplatesRecursive(file, name, templates);
			}
			else if (name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".tpl") || name.endsWith(".txt")
					|| name.endsWith(".json")) {
				Chart.Template template = Chart.Template.builder()
					.name(name)
					.data(Files.readString(file.toPath()))
					.build();
				templates.add(template);
			}
		}
	}

	// Dirs whose files are NOT exposed via .Files. Helm excludes only templates/ and
	// charts/ — crds/ files ARE included in .Files (Helm's loader routes them to the
	// default/Files case), so templates can read them: e.g. projectcapsule/capsule wraps
	// each CRD in a ConfigMap via `.Files.Glob "crds/**.yaml"`. crds/ is still loaded
	// separately as Chart.Crd for CRD installation; this only governs .Files visibility.
	private static final Set<String> EXCLUDED_DIRS = Set.of("templates", "charts");

	private static final Set<String> EXCLUDED_FILES = Set.of("Chart.yaml", "Chart.lock", "values.yaml",
			"values.schema.json", "README.md", ".helmignore");

	private void loadChartFiles(File chartDir, Map<String, String> chartFiles) throws IOException {
		File[] entries = chartDir.listFiles();
		if (entries == null) {
			return;
		}
		for (File entry : entries) {
			if (entry.isDirectory() && !EXCLUDED_DIRS.contains(entry.getName())) {
				loadFilesRecursive(entry, entry.getName(), chartFiles);
			}
			else if (entry.isFile() && !EXCLUDED_FILES.contains(entry.getName())) {
				readTextFile(entry, entry.getName(), chartFiles);
			}
		}
	}

	private void loadFilesRecursive(File dir, String path, Map<String, String> chartFiles) throws IOException {
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			String name = path + "/" + file.getName();
			if (file.isDirectory()) {
				loadFilesRecursive(file, name, chartFiles);
			}
			else {
				readTextFile(file, name, chartFiles);
			}
		}
	}

	private void readTextFile(File file, String name, Map<String, String> chartFiles) throws IOException {
		try {
			chartFiles.put(name, Files.readString(file.toPath()));
		}
		catch (MalformedInputException ex) {
			log.debug("Skipping binary file: {}", name);
		}
	}

	private void loadCrdsRecursive(File dir, String path, List<Chart.Crd> crds) throws IOException {
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			String name = path.isEmpty() ? file.getName() : path + "/" + file.getName();
			if (file.isDirectory()) {
				loadCrdsRecursive(file, name, crds);
			}
			else if (name.endsWith(".yaml") || name.endsWith(".yml")) {
				Chart.Crd crd = Chart.Crd.builder().name(name).data(Files.readString(file.toPath())).build();
				crds.add(crd);
			}
		}
	}

}
