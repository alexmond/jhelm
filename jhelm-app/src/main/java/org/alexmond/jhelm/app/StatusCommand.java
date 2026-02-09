package org.alexmond.jhelm.app;

import lombok.extern.slf4j.Slf4j;
import org.alexmond.jhelm.core.Release;
import org.alexmond.jhelm.kube.HelmKubeService;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.Optional;

@Component
@CommandLine.Command(name = "status", description = "display the status of the named release")
@Slf4j
public class StatusCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "release name")
    private String name;

    @CommandLine.Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "namespace")
    private String namespace;

    private final HelmKubeService helmKubeService;

    public StatusCommand(HelmKubeService helmKubeService) {
        this.helmKubeService = helmKubeService;
    }

    @Override
    public void run() {
        try {
            Optional<Release> releaseOpt = helmKubeService.getRelease(name, namespace);
            if (releaseOpt.isEmpty()) {
                log.error("Error: release not found: {}", name);
                return;
            }

            Release r = releaseOpt.get();
            log.info("NAME: {}", r.getName());
            log.info("LAST DEPLOYED: {}", r.getInfo().getLastDeployed());
            log.info("NAMESPACE: {}", r.getNamespace());
            log.info("STATUS: {}", r.getInfo().getStatus());
            log.info("REVISION: {}", r.getVersion());
            log.info("\nMANIFEST:\n{}", r.getManifest());
        } catch (Exception e) {
            log.error("Error fetching status: {}", e.getMessage());
        }
    }
}
