package org.alexmond.jhelm.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.Chart;
import org.alexmond.jhelm.core.ChartLoader;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.File;
import java.util.Map;

@Component
@CommandLine.Command(name = "show",
        description = "Show information about a chart",
        subcommands = {
                ShowCommand.ChartCommand.class,
                ShowCommand.ValuesCommand.class,
                ShowCommand.ReadmeCommand.class,
                ShowCommand.CrdsCommand.class,
                ShowCommand.AllCommand.class
        })
@Slf4j
public class ShowCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Component
    @CommandLine.Command(name = "chart", description = "Show the chart's Chart.yaml")
    @Slf4j
    public static class ChartCommand implements Runnable {
        private final ChartLoader chartLoader;

        @CommandLine.Parameters(index = "0", description = "chart path")
        String chartPath;

        @CommandLine.Option(names = {"--version"}, description = "specify chart version")
        private String version;

        @CommandLine.Option(names = {"--repo"}, description = "chart repository URL")
        private String repo;

        public ChartCommand(ChartLoader chartLoader) {
            this.chartLoader = chartLoader;
        }

        @Override
        public void run() {
            try {
                Chart chart = loadChart(chartPath, version, repo);
                String yaml = toYaml(chart.getMetadata());
                System.out.println(yaml);
            } catch (Exception e) {
                log.error("Error showing chart: {}", e.getMessage());
            }
        }
    }

    @Component
    @CommandLine.Command(name = "values", description = "Show the chart's values.yaml")
    @Slf4j
    public static class ValuesCommand implements Runnable {
        private final ChartLoader chartLoader;

        @CommandLine.Parameters(index = "0", description = "chart path")
        String chartPath;

        @CommandLine.Option(names = {"--version"}, description = "specify chart version")
        private String version;

        @CommandLine.Option(names = {"--repo"}, description = "chart repository URL")
        private String repo;

        @CommandLine.Option(names = {"--jsonpath"}, description = "extract specific values using JSONPath")
        private String jsonpath;

        public ValuesCommand(ChartLoader chartLoader) {
            this.chartLoader = chartLoader;
        }

        @Override
        public void run() {
            try {
                Chart chart = loadChart(chartPath, version, repo);
                Map<String, Object> values = chart.getValues();
                if (values == null || values.isEmpty()) {
                    System.out.println("{}");
                    return;
                }
                String yaml = toYaml(values);
                System.out.println(yaml);
            } catch (Exception e) {
                log.error("Error showing values: {}", e.getMessage());
            }
        }
    }

    @Component
    @CommandLine.Command(name = "readme", description = "Show the chart's README")
    @Slf4j
    public static class ReadmeCommand implements Runnable {
        private final ChartLoader chartLoader;

        @CommandLine.Parameters(index = "0", description = "chart path")
        String chartPath;

        @CommandLine.Option(names = {"--version"}, description = "specify chart version")
        private String version;

        @CommandLine.Option(names = {"--repo"}, description = "chart repository URL")
        private String repo;

        public ReadmeCommand(ChartLoader chartLoader) {
            this.chartLoader = chartLoader;
        }

        @Override
        public void run() {
            try {
                Chart chart = loadChart(chartPath, version, repo);
                String readme = chart.getReadme();
                if (readme == null || readme.isEmpty()) {
                    System.out.println("No README found in chart");
                    return;
                }
                System.out.println(readme);
            } catch (Exception e) {
                log.error("Error showing readme: {}", e.getMessage());
            }
        }
    }

    @Component
    @CommandLine.Command(name = "crds", description = "Show the chart's Custom Resource Definitions")
    @Slf4j
    public static class CrdsCommand implements Runnable {
        private final ChartLoader chartLoader;

        @CommandLine.Parameters(index = "0", description = "chart path")
        String chartPath;

        @CommandLine.Option(names = {"--version"}, description = "specify chart version")
        private String version;

        @CommandLine.Option(names = {"--repo"}, description = "chart repository URL")
        private String repo;

        public CrdsCommand(ChartLoader chartLoader) {
            this.chartLoader = chartLoader;
        }

        @Override
        public void run() {
            try {
                Chart chart = loadChart(chartPath, version, repo);
                if (chart.getCrds() == null || chart.getCrds().isEmpty()) {
                    System.out.println("No CRDs found in chart");
                    return;
                }
                for (Chart.Crd crd : chart.getCrds()) {
                    System.out.println(crd.getData());
                    if (chart.getCrds().indexOf(crd) < chart.getCrds().size() - 1) {
                        System.out.println("---");
                    }
                }
            } catch (Exception e) {
                log.error("Error showing crds: {}", e.getMessage());
            }
        }
    }

    @Component
    @CommandLine.Command(name = "all", description = "Show all information about the chart")
    @Slf4j
    public static class AllCommand implements Runnable {
        private final ChartLoader chartLoader;

        @CommandLine.Parameters(index = "0", description = "chart path")
        String chartPath;

        @CommandLine.Option(names = {"--version"}, description = "specify chart version")
        private String version;

        @CommandLine.Option(names = {"--repo"}, description = "chart repository URL")
        private String repo;

        public AllCommand(ChartLoader chartLoader) {
            this.chartLoader = chartLoader;
        }

        @Override
        public void run() {
            try {
                Chart chart = loadChart(chartPath, version, repo);

                // Show Chart.yaml
                System.out.println("# Chart.yaml");
                String chartYaml = toYaml(chart.getMetadata());
                System.out.println(chartYaml);

                // Show values.yaml
                System.out.println("---");
                System.out.println("# values.yaml");
                if (chart.getValues() != null && !chart.getValues().isEmpty()) {
                    String valuesYaml = toYaml(chart.getValues());
                    System.out.println(valuesYaml);
                } else {
                    System.out.println("{}");
                }

                // Show README
                if (chart.getReadme() != null && !chart.getReadme().isEmpty()) {
                    System.out.println("---");
                    System.out.println("# README.md");
                    System.out.println(chart.getReadme());
                }

                // Show CRDs
                if (chart.getCrds() != null && !chart.getCrds().isEmpty()) {
                    System.out.println("---");
                    System.out.println("# CRDs");
                    for (Chart.Crd crd : chart.getCrds()) {
                        System.out.println(crd.getData());
                        if (chart.getCrds().indexOf(crd) < chart.getCrds().size() - 1) {
                            System.out.println("---");
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error showing all: {}", e.getMessage());
            }
        }
    }

    private static Chart loadChart(String chartPath, String version, String repo) throws Exception {
        ChartLoader loader = new ChartLoader();
        File chartDir = new File(chartPath);
        return loader.load(chartDir);
    }

    private static String toYaml(Object obj) throws Exception {
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .build();
        ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);
        return yamlMapper.writeValueAsString(obj);
    }
}
