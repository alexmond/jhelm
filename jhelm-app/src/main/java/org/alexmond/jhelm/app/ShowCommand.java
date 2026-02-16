package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.ShowAction;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

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
        private final ShowAction showAction;

        @CommandLine.Parameters(index = "0", description = "chart path")
        String chartPath;

        public ChartCommand(ShowAction showAction) {
            this.showAction = showAction;
        }

        @Override
        public void run() {
            try {
                System.out.println(showAction.showChart(chartPath));
            } catch (Exception e) {
                log.error("Error showing chart: {}", e.getMessage());
            }
        }
    }

    @Component
    @CommandLine.Command(name = "values", description = "Show the chart's values.yaml")
    @Slf4j
    public static class ValuesCommand implements Runnable {
        private final ShowAction showAction;

        @CommandLine.Parameters(index = "0", description = "chart path")
        String chartPath;

        public ValuesCommand(ShowAction showAction) {
            this.showAction = showAction;
        }

        @Override
        public void run() {
            try {
                System.out.println(showAction.showValues(chartPath));
            } catch (Exception e) {
                log.error("Error showing values: {}", e.getMessage());
            }
        }
    }

    @Component
    @CommandLine.Command(name = "readme", description = "Show the chart's README")
    @Slf4j
    public static class ReadmeCommand implements Runnable {
        private final ShowAction showAction;

        @CommandLine.Parameters(index = "0", description = "chart path")
        String chartPath;

        public ReadmeCommand(ShowAction showAction) {
            this.showAction = showAction;
        }

        @Override
        public void run() {
            try {
                String readme = showAction.showReadme(chartPath);
                if (readme.isEmpty()) {
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
        private final ShowAction showAction;

        @CommandLine.Parameters(index = "0", description = "chart path")
        String chartPath;

        public CrdsCommand(ShowAction showAction) {
            this.showAction = showAction;
        }

        @Override
        public void run() {
            try {
                String crds = showAction.showCrds(chartPath);
                if (crds.isEmpty()) {
                    System.out.println("No CRDs found in chart");
                    return;
                }
                System.out.println(crds);
            } catch (Exception e) {
                log.error("Error showing crds: {}", e.getMessage());
            }
        }
    }

    @Component
    @CommandLine.Command(name = "all", description = "Show all information about the chart")
    @Slf4j
    public static class AllCommand implements Runnable {
        private final ShowAction showAction;

        @CommandLine.Parameters(index = "0", description = "chart path")
        String chartPath;

        public AllCommand(ShowAction showAction) {
            this.showAction = showAction;
        }

        @Override
        public void run() {
            try {
                System.out.println(showAction.showAll(chartPath));
            } catch (Exception e) {
                log.error("Error showing all: {}", e.getMessage());
            }
        }
    }
}
