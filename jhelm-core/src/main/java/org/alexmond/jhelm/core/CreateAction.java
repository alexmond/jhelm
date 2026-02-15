package org.alexmond.jhelm.core;

import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class CreateAction {

    public void create(Path chartPath) throws IOException {
        String chartName = chartPath.getFileName().toString();

        if (Files.exists(chartPath)) {
            throw new IOException("directory " + chartName + " already exists");
        }

        log.info("Creating {}", chartName);

        // Create main directories
        Files.createDirectories(chartPath);
        Files.createDirectories(chartPath.resolve("templates"));
        Files.createDirectories(chartPath.resolve("templates/tests"));

        // Create .helmignore
        Files.writeString(chartPath.resolve(".helmignore"),
                HelmChartTemplates.getHelmIgnore());

        // Create Chart.yaml
        Files.writeString(chartPath.resolve("Chart.yaml"),
                HelmChartTemplates.substituteChartName(HelmChartTemplates.getChartYaml(), chartName));

        // Create values.yaml
        Files.writeString(chartPath.resolve("values.yaml"),
                HelmChartTemplates.substituteChartName(HelmChartTemplates.getValuesYaml(), chartName));

        // Create template files
        Files.writeString(chartPath.resolve("templates/NOTES.txt"),
                HelmChartTemplates.substituteChartName(HelmChartTemplates.getNotesTemplate(), chartName));
        Files.writeString(chartPath.resolve("templates/_helpers.tpl"),
                HelmChartTemplates.substituteChartName(HelmChartTemplates.getHelpersTemplate(), chartName));
        Files.writeString(chartPath.resolve("templates/deployment.yaml"),
                HelmChartTemplates.substituteChartName(HelmChartTemplates.getDeploymentTemplate(), chartName));
        Files.writeString(chartPath.resolve("templates/service.yaml"),
                HelmChartTemplates.substituteChartName(HelmChartTemplates.getServiceTemplate(), chartName));
        Files.writeString(chartPath.resolve("templates/serviceaccount.yaml"),
                HelmChartTemplates.substituteChartName(HelmChartTemplates.getServiceAccountTemplate(), chartName));
        Files.writeString(chartPath.resolve("templates/hpa.yaml"),
                HelmChartTemplates.substituteChartName(HelmChartTemplates.getHpaTemplate(), chartName));
        Files.writeString(chartPath.resolve("templates/ingress.yaml"),
                HelmChartTemplates.substituteChartName(HelmChartTemplates.getIngressTemplate(), chartName));
        Files.writeString(chartPath.resolve("templates/httproute.yaml"),
                HelmChartTemplates.substituteChartName(HelmChartTemplates.getHttpRouteTemplate(), chartName));
        Files.writeString(chartPath.resolve("templates/tests/test-connection.yaml"),
                HelmChartTemplates.substituteChartName(HelmChartTemplates.getTestConnectionTemplate(), chartName));
    }
}
