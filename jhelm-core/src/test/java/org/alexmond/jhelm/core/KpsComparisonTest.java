package org.alexmond.jhelm.core;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KpsComparisonTest {

    private final ChartLoader chartLoader = new ChartLoader();
    private final Engine engine = new Engine();
    private final InstallAction installAction = new InstallAction(engine);

    @Test
    void testSimpleRendering() throws IOException {
        Chart chart = new Chart();
        chart.setMetadata(new ChartMetadata());
        chart.getMetadata().setName("simple");
        chart.setValues(Map.of("enabled", true, "name", "world"));
        
        Chart.Template t = new Chart.Template();
        t.setName("hello.yaml");
        t.setData("hello {{ .Values.name }} {{ if .Values.enabled }}enabled{{ end }}");
        chart.getTemplates().add(t);
        
        Release release = installAction.install(chart, "simple", "default", Map.of(), 1);
        System.out.println("Simple manifest: [" + release.getManifest().trim() + "]");
        assertTrue(release.getManifest().contains("hello world enabled"));
    }

    @Test
    void compareAllTopCharts() throws IOException {
        String[] charts = {
            "prometheus", "grafana", "nginx", "mongodb", "postgresql",
            "redis", "mysql", "mariadb", "apache", "jenkins",
            "kafka", "rabbitmq", "elasticsearch", "drupal", "wordpress",
            "joomla", "ghost", "moodle", "magento", "redmine",
            "kube-prometheus-stack", "argo-cd"
        };

        for (String chartName : charts) {
            compareChart(chartName, "release-" + chartName);
        }
    }

    private void compareChart(String chartName, String releaseName) throws IOException {
        File chartDir = new File("sample-charts/" + chartName);
        if (!chartDir.exists()) {
             chartDir = new File("../sample-charts/" + chartName);
        }
        
        if (!chartDir.exists()) {
            System.out.println("Skipping chart " + chartName + " - directory not found");
            return;
        }

        Chart chart = chartLoader.load(chartDir);
        assertNotNull(chart);

        try {
            Release release = installAction.install(chart, releaseName, "default", Map.of(), 1);
            assertNotNull(release);
            
            System.out.println(chartName + " - Manifest length: " + release.getManifest().length());
            if (release.getManifest().length() == 0) {
                System.out.println(chartName + " - Values: " + chart.getValues().keySet());
            }
            
            File actualFile = new File("actual_" + chartName + ".yaml");
            Files.writeString(actualFile.toPath(), release.getManifest());
            System.out.println(chartName + " - Actual manifest written to: " + actualFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            fail(chartName + " - Rendering failed: " + e.getMessage());
        }
    }
}
