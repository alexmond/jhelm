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

    @CommandLine.Option(names = {"--dry-run"}, description = "simulate an upgrade")
    private boolean dryRun;

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
                    Release release = installAction.install(chart, name, namespace, new HashMap<>(), 1, dryRun);
                    if (dryRun) {
                        log.info("NAME: {}", release.getName());
                        log.info("LAST DEPLOYED: {}", release.getInfo().getLastDeployed());
                        log.info("NAMESPACE: {}", release.getNamespace());
                        log.info("STATUS: {}", release.getInfo().getStatus());
                        log.info("REVISION: {}", release.getVersion());
                        log.info("\nMANIFEST:\n{}", release.getManifest());
                    } else {
                        log.info("Release \"{}\" does not exist. Installing it now.", name);
                    }
                } else {
                    log.error("Error: release \"{}\" does not exist", name);
                }
                return;
            }

            Release upgradedRelease = upgradeAction.upgrade(currentReleaseOpt.get(), chart, new HashMap<>(), dryRun);
            
            if (dryRun) {
                log.info("NAME: {}", upgradedRelease.getName());
                log.info("LAST DEPLOYED: {}", upgradedRelease.getInfo().getLastDeployed());
                log.info("NAMESPACE: {}", upgradedRelease.getNamespace());
                log.info("STATUS: {}", upgradedRelease.getInfo().getStatus());
                log.info("REVISION: {}", upgradedRelease.getVersion());
                log.info("\nMANIFEST:\n{}", upgradedRelease.getManifest());
            } else {
                log.info("Release \"{}\" has been upgraded. Happy Helming!", name);
            }
        } catch (Exception e) {
            log.error("Error upgrading release: {}", e.getMessage(), e);
        }
    }
}
