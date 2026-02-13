package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@CommandLine.Command(
        name = "create",
        description = "Create a new chart with the given name"
)
public class CreateCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "The chart name")
    private String name;

    @CommandLine.Option(names = {"-p", "--starter"}, description = "The name or absolute path to Helm starter scaffold")
    private String starter;

    @Override
    public void run() {
        try {
            Path chartPath = Paths.get(name);

            if (Files.exists(chartPath)) {
                log.error("Error: directory {} already exists", name);
                System.exit(1);
            }

            log.info("Creating {}", name);
            createChartStructure(chartPath);

            System.out.println("Creating " + name);

        } catch (IOException e) {
            log.error("Error creating chart: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private void createChartStructure(Path chartPath) throws IOException {
        String chartName = chartPath.getFileName().toString();

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
