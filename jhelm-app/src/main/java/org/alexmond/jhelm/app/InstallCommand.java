package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.Chart;
import org.alexmond.jhelm.core.ChartLoader;
import org.alexmond.jhelm.core.InstallAction;
import org.alexmond.jhelm.core.Release;
import org.alexmond.jhelm.kube.HelmKubeService;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.File;
import java.util.HashMap;

@Component
@CommandLine.Command(name = "install", description = "install a chart")
@Slf4j
public class InstallCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;

    @CommandLine.Parameters(index = "1", description = "chart path")
    private String chartPath;

    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    private final HelmKubeService helmKubeService;
    private final InstallAction installAction;

    public InstallCommand(HelmKubeService helmKubeService, InstallAction installAction) {
        this.helmKubeService = helmKubeService;
        this.installAction = installAction;
    }

    @Override
    public void run() {
        try {
            ChartLoader loader = new ChartLoader();
            Chart chart = loader.load(new File(chartPath));
            
            installAction.install(chart, name, namespace, new HashMap<>(), 1);
            
            log.info("Release \"{}\" has been installed.", name);
        } catch (Exception e) {
            log.error("Error installing chart: {}", e.getMessage(), e);
        }
    }
}
