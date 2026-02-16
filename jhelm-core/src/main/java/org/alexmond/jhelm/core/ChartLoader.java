package org.alexmond.jhelm.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChartLoader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public Chart load(File chartDir) throws IOException {
        if (!chartDir.exists() || !chartDir.isDirectory()) {
            throw new IllegalArgumentException("Chart directory does not exist: " + chartDir.getPath());
        }

        // Load Chart.yaml
        File metadataFile = new File(chartDir, "Chart.yaml");
        if (!metadataFile.exists()) {
            throw new IllegalArgumentException("Chart.yaml not found in " + chartDir.getPath());
        }
        ChartMetadata metadata = yamlMapper.readValue(metadataFile, ChartMetadata.class);

        // Load values.yaml
        File valuesFile = new File(chartDir, "values.yaml");
        Map<String, Object> values = new HashMap<>();
        if (valuesFile.exists()) {
            values = yamlMapper.readValue(valuesFile, Map.class);
        }

        // Load templates
        File templatesDir = new File(chartDir, "templates");
        List<Chart.Template> templates = new ArrayList<>();
        if (templatesDir.exists() && templatesDir.isDirectory()) {
            loadTemplatesRecursive(templatesDir, "", templates);
        }

        // Load dependencies (subcharts)
        File chartsDir = new File(chartDir, "charts");
        List<Chart> dependencies = new ArrayList<>();
        if (chartsDir.exists() && chartsDir.isDirectory()) {
            File[] subchartDirs = chartsDir.listFiles(File::isDirectory);
            if (subchartDirs != null) {
                for (File subchartDir : subchartDirs) {
                    dependencies.add(load(subchartDir));
                }
            }
        }

        // Load README
        String readme = null;
        File readmeFile = new File(chartDir, "README.md");
        if (readmeFile.exists()) {
            readme = Files.readString(readmeFile.toPath());
        }

        // Load CRDs
        File crdsDir = new File(chartDir, "crds");
        List<Chart.Crd> crds = new ArrayList<>();
        if (crdsDir.exists() && crdsDir.isDirectory()) {
            loadCrdsRecursive(crdsDir, "", crds);
        }

        return Chart.builder()
                .metadata(metadata)
                .values(values)
                .templates(templates)
                .dependencies(dependencies)
                .readme(readme)
                .crds(crds)
                .build();
    }

    private void loadTemplatesRecursive(File dir, String path, List<Chart.Template> templates) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = path.isEmpty() ? file.getName() : path + "/" + file.getName();
            if (file.isDirectory()) {
                loadTemplatesRecursive(file, name, templates);
            } else if (name.endsWith(".yaml") || name.endsWith(".tpl")) {
                Chart.Template template = Chart.Template.builder()
                        .name(name)
                        .data(Files.readString(file.toPath()))
                        .build();
                templates.add(template);
            }
        }
    }

    private void loadCrdsRecursive(File dir, String path, List<Chart.Crd> crds) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = path.isEmpty() ? file.getName() : path + "/" + file.getName();
            if (file.isDirectory()) {
                loadCrdsRecursive(file, name, crds);
            } else if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                Chart.Crd crd = Chart.Crd.builder()
                        .name(name)
                        .data(Files.readString(file.toPath()))
                        .build();
                crds.add(crd);
            }
        }
    }
}
