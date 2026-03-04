package org.alexmond.jhelm.core.service;

import tools.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.exception.ChartLoadException;
import org.alexmond.jhelm.core.model.Chart;
import org.alexmond.jhelm.core.model.ChartMetadata;
import org.alexmond.jhelm.core.util.ValuesLoader;

@Slf4j
@Component
public class ChartLoader {

	private final YAMLMapper yamlMapper = YAMLMapper.builder().build();

	public Chart load(File chartDir) throws IOException {
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
		List<Chart> dependencies = loadDependencies(chartDir);

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

	private List<Chart> loadDependencies(File chartDir) throws IOException {
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
			}
		}
		return dependencies;
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

	private static final Set<String> EXCLUDED_DIRS = Set.of("templates", "charts", "crds");

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
