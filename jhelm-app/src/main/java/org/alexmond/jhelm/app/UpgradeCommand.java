package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.*;
import org.alexmond.jhelm.kube.HelmKubeService;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.File;
import java.util.HashMap;
import java.util.Optional;

@Component
@CommandLine.Command(name = "upgrade", description = "upgrade a release")
@Slf4j
public class UpgradeCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;

    @CommandLine.Parameters(index = "1", description = "chart path")
    private String chartPath;

    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    @CommandLine.Option(names = {"--install"}, description = "install if not exists")
    private boolean install;

    private final HelmKubeService helmKubeService;
    private final InstallAction installAction;
    private final UpgradeAction upgradeAction;

    public UpgradeCommand(HelmKubeService helmKubeService, InstallAction installAction, UpgradeAction upgradeAction) {
        this.helmKubeService = helmKubeService;
        this.installAction = installAction;
        this.upgradeAction = upgradeAction;
    }

    @Override
    public void run() {
        try {
            Optional<Release> currentReleaseOpt = helmKubeService.getRelease(name, namespace);
            ChartLoader loader = new ChartLoader();
            Chart chart = loader.load(new File(chartPath));

            if (currentReleaseOpt.isEmpty()) {
                if (install) {
                    installAction.install(chart, name, namespace, new HashMap<>(), 1);
                    log.info("Release \"{}\" does not exist. Installing it now.", name);
                } else {
                    log.error("Error: release \"{}\" does not exist", name);
                }
                return;
            }

            upgradeAction.upgrade(currentReleaseOpt.get(), chart, new HashMap<>());
            
            log.info("Release \"{}\" has been upgraded. Happy Helming!", name);
        } catch (Exception e) {
            log.error("Error upgrading release: {}", e.getMessage(), e);
        }
    }
}
