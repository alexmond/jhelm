package org.alexmond.jhelm.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChartLoader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public Chart load(File chartDir) throws IOException {
        if (!chartDir.exists() || !chartDir.isDirectory()) {
            throw new IllegalArgumentException("Chart directory does not exist: " + chartDir.getPath());
        }

        Chart chart = new Chart();
        
        // Load Chart.yaml
        File metadataFile = new File(chartDir, "Chart.yaml");
        if (!metadataFile.exists()) {
             throw new IllegalArgumentException("Chart.yaml not found in " + chartDir.getPath());
        }
        chart.setMetadata(yamlMapper.readValue(metadataFile, ChartMetadata.class));

        // Load values.yaml
        File valuesFile = new File(chartDir, "values.yaml");
        Map<String, Object> values = new HashMap<>();
        if (valuesFile.exists()) {
            values = yamlMapper.readValue(valuesFile, Map.class);
        }
        chart.setValues(values);

        // Load templates
        File templatesDir = new File(chartDir, "templates");
        List<Chart.Template> templates = new ArrayList<>();
        if (templatesDir.exists() && templatesDir.isDirectory()) {
            loadTemplatesRecursive(templatesDir, "", templates);
        }
        chart.setTemplates(templates);
        
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
        chart.setDependencies(dependencies);

        return chart;
    }
    private void loadTemplatesRecursive(File dir, String path, List<Chart.Template> templates) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = path.isEmpty() ? file.getName() : path + "/" + file.getName();
            if (file.isDirectory()) {
                loadTemplatesRecursive(file, name, templates);
            } else if (name.endsWith(".yaml") || name.endsWith(".tpl")) {
                Chart.Template template = new Chart.Template();
                template.setName(name);
                template.setData(Files.readString(file.toPath()));
                templates.add(template);
            }
        }
    }
}
